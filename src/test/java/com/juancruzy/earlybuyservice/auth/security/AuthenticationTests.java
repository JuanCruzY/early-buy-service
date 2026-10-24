package com.juancruzy.earlybuyservice.auth.security;

import java.util.List;
import java.util.Map;

import com.juancruzy.earlybuyservice.auth.client.AuthClient;
import com.juancruzy.earlybuyservice.auth.dto.AuthUserResponse;
import com.juancruzy.earlybuyservice.auth.exception.AuthUnavailableException;
import com.juancruzy.earlybuyservice.auth.exception.InvalidJwtException;
import com.juancruzy.earlybuyservice.shared.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthenticationTests.SubscriptionTestController.class)
@Import({SecurityConfig.class, AuthAuthenticationProvider.class, SecurityErrorHandler.class,
        AuthenticationTests.SubscriptionTestController.class})
class AuthenticationTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthClient authClient;

    @Test
    void rejectsMissingCredentialsBeforeCallingAuth() throws Exception {
        mockMvc.perform(get("/v1/subscriptions"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(jsonPath("$.code").value("MISSING_JWT"))
                .andExpect(jsonPath("$.path").value("/v1/subscriptions"));
        verifyNoInteractions(authClient);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "Bearer", "Bearer "})
    void rejectsEmptyCredentials(String authorization) throws Exception {
        mockMvc.perform(get("/v1/subscriptions").header(HttpHeaders.AUTHORIZATION, authorization))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("MISSING_JWT"));
        verifyNoInteractions(authClient);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Basic abc", "Bearer one two", "Bearer token,other", "token", "Bearer <token>"})
    void rejectsMalformedAuthorization(String authorization) throws Exception {
        mockMvc.perform(get("/v1/subscriptions").header(HttpHeaders.AUTHORIZATION, authorization))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_JWT"));
        verifyNoInteractions(authClient);
    }

    @Test
    void rejectsMultipleAuthorizationHeaders() throws Exception {
        mockMvc.perform(get("/v1/subscriptions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer one", "Bearer two"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_JWT"));
        verifyNoInteractions(authClient);
    }

    @Test
    void exposesAuthenticatedIdentityWithoutCreatingAnHttpSession() throws Exception {
        when(authClient.currentUser("example-token"))
                .thenReturn(new AuthUserResponse("user-from-auth", List.of("user"), true));

        var result = mockMvc.perform(get("/v1/subscriptions")
                        .header(HttpHeaders.AUTHORIZATION, "bearer example-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("user-from-auth"))
                .andExpect(cookie().doesNotExist("JSESSIONID"))
                .andReturn();

        assertThat(result.getRequest().getSession(false)).isNull();
        mockMvc.perform(get("/v1/subscriptions")).andExpect(status().isUnauthorized());
        verify(authClient, times(1)).currentUser("example-token");
    }

    @Test
    void allowsPostAndDeleteWithoutCsrfTokensWhenBearerIsValid() throws Exception {
        when(authClient.currentUser("example-token"))
                .thenReturn(new AuthUserResponse("user-from-auth", List.of("user"), true));

        mockMvc.perform(post("/v1/subscriptions").header(HttpHeaders.AUTHORIZATION, "Bearer example-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("user-from-auth"));
        mockMvc.perform(delete("/v1/subscriptions/subscription-1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer example-token"))
                .andExpect(status().isOk());
    }

    @Test
    void protectsSubscriptionSubpaths() throws Exception {
        mockMvc.perform(delete("/v1/subscriptions/subscription-1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("MISSING_JWT"));
        verifyNoInteractions(authClient);
    }

    @Test
    void rejectsAuthenticatedUsersWithoutUserPermission() throws Exception {
        when(authClient.currentUser("example-token"))
                .thenReturn(new AuthUserResponse("user-from-auth", List.of("admin"), true));

        mockMvc.perform(get("/v1/subscriptions").header(HttpHeaders.AUTHORIZATION, "Bearer example-token"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void treatsNullPermissionsAsNoPermissions() throws Exception {
        when(authClient.currentUser("example-token"))
                .thenReturn(new AuthUserResponse("user-from-auth", null, true));

        mockMvc.perform(get("/v1/subscriptions").header(HttpHeaders.AUTHORIZATION, "Bearer example-token"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void rejectsDisabledIdentityEvenIfAuthReturnsSuccess() throws Exception {
        when(authClient.currentUser("example-token"))
                .thenReturn(new AuthUserResponse("user-from-auth", List.of("user"), false));

        mockMvc.perform(get("/v1/subscriptions").header(HttpHeaders.AUTHORIZATION, "Bearer example-token"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void consultsAuthAgainAndRejectsARevokedTokenOnTheNextRequest() throws Exception {
        when(authClient.currentUser("example-token"))
                .thenReturn(new AuthUserResponse("user-from-auth", List.of("user"), true))
                .thenThrow(new InvalidJwtException());

        mockMvc.perform(get("/v1/subscriptions").header(HttpHeaders.AUTHORIZATION, "Bearer example-token"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/v1/subscriptions").header(HttpHeaders.AUTHORIZATION, "Bearer example-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_JWT"));
        verify(authClient, times(2)).currentUser("example-token");
    }

    @Test
    void returnsUnavailableInsteadOfInvalidCredentialsWhenAuthFails() throws Exception {
        when(authClient.currentUser("example-token"))
                .thenThrow(new AuthUnavailableException());

        mockMvc.perform(get("/v1/subscriptions").header(HttpHeaders.AUTHORIZATION, "Bearer example-token"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.code").value("AUTH_UNAVAILABLE"))
                .andExpect(jsonPath("$.message").value("No se pudo validar la sesión con Auth"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/actuator/health", "/v3/api-docs", "/swagger-ui/test"})
    void leavesPublicEndpointsAccessibleWithoutCallingAuth(String path) throws Exception {
        mockMvc.perform(get(path)).andExpect(status().isOk());
        verifyNoInteractions(authClient);
    }

    @Test
    void allowsOptionsWithoutAuthentication() throws Exception {
        mockMvc.perform(options("/v1/subscriptions")).andExpect(status().isOk());
        verifyNoInteractions(authClient);
    }

    @RestController
    @TestComponent
    static class SubscriptionTestController {

        @GetMapping("/v1/subscriptions")
        public Map<String, String> current(@AuthenticationPrincipal AuthenticatedUser user) {
            return Map.of("userId", user.id());
        }

        @PostMapping("/v1/subscriptions")
        public Map<String, String> create(@AuthenticationPrincipal AuthenticatedUser user) {
            return Map.of("userId", user.id());
        }

        @DeleteMapping("/v1/subscriptions/{subscriptionId}")
        public void cancel() {
        }

        @GetMapping({"/actuator/health", "/v3/api-docs", "/swagger-ui/test"})
        public Map<String, String> publicEndpoint() {
            return Map.of("status", "UP");
        }
    }
}
