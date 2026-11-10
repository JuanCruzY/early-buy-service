package com.juancruzy.earlybuyservice.subscription.controller;

import java.util.List;

import com.juancruzy.earlybuyservice.auth.client.AuthClient;
import com.juancruzy.earlybuyservice.auth.dto.AuthUserResponse;
import com.juancruzy.earlybuyservice.auth.exception.InvalidJwtException;
import com.juancruzy.earlybuyservice.auth.security.AuthAuthenticationProvider;
import com.juancruzy.earlybuyservice.auth.security.SecurityErrorHandler;
import com.juancruzy.earlybuyservice.catalog.service.CatalogValidationService;
import com.juancruzy.earlybuyservice.shared.config.SecurityConfig;
import com.juancruzy.earlybuyservice.subscription.model.NotificationChannel;
import com.juancruzy.earlybuyservice.subscription.model.Subscription;
import com.juancruzy.earlybuyservice.subscription.model.SubscriptionStatus;
import com.juancruzy.earlybuyservice.subscription.repository.SubscriptionRepository;
import com.juancruzy.earlybuyservice.subscription.service.SubscriptionService;
import com.juancruzy.earlybuyservice.subscription.validation.ContactValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(SubscriptionController.class)
@Import({SecurityConfig.class, AuthAuthenticationProvider.class, SecurityErrorHandler.class,
        SubscriptionService.class, ContactValidator.class})
class ListSubscriptionsTests {
    @Autowired MockMvc mvc;
    @MockitoBean AuthClient auth;
    @MockitoBean CatalogValidationService catalog;
    @MockitoBean SubscriptionRepository repository;

    @BeforeEach
    void setUp() {
        when(auth.currentUser("token")).thenReturn(new AuthUserResponse("owner", List.of("user"), true));
    }

    @Test
    void usesAuthenticatedOwnerAndReturnsExactlyTheFourApprovedFields() throws Exception {
        var subscription = new Subscription("article", "owner", "user@example.com", NotificationChannel.EMAIL);
        ReflectionTestUtils.setField(subscription, "subscriptionId", "subscription-id");
        when(repository.findAllByUserIdAndStatus("owner", SubscriptionStatus.SUBSCRIBED))
                .thenReturn(List.of(subscription));
        var response = mvc.perform(get("/v1/subscriptions").header("Authorization", "Bearer token")
                        .param("userId", "another-user"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andReturn().getResponse().getContentAsString();
        var expected = """
                [{"subscriptionId":"subscription-id","articleId":"article",
                  "channel":"EMAIL","contact":"user@example.com"}]
                """;
        var mapper = JsonMapper.builder().build();
        assertThat(mapper.readTree(response)).isEqualTo(mapper.readTree(expected));
        verify(repository).findAllByUserIdAndStatus("owner", SubscriptionStatus.SUBSCRIBED);
        verifyNoMoreInteractions(repository);
        verifyNoInteractions(catalog);
    }

    @Test
    void returnsAnEmptyArrayWhenThereAreNoActiveSubscriptions() throws Exception {
        when(repository.findAllByUserIdAndStatus("owner", SubscriptionStatus.SUBSCRIBED)).thenReturn(List.of());
        mvc.perform(get("/v1/subscriptions").header("Authorization", "Bearer token"))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
        verifyNoInteractions(catalog);
    }

    @Test
    void rejectsMissingOrInvalidCredentialsBeforeAccessingMongo() throws Exception {
        mvc.perform(get("/v1/subscriptions")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("MISSING_JWT"));
        when(auth.currentUser("token")).thenThrow(new InvalidJwtException());
        mvc.perform(get("/v1/subscriptions").header("Authorization", "Bearer token"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_JWT"));
        verifyNoInteractions(repository, catalog);
    }

    @Test
    void rejectsUsersWithoutTheUserPermission() throws Exception {
        when(auth.currentUser("token")).thenReturn(new AuthUserResponse("owner", List.of("admin"), true));
        mvc.perform(get("/v1/subscriptions").header("Authorization", "Bearer token"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        verifyNoInteractions(repository, catalog);
    }

    @Test
    void doesNotPresentDatabaseFailuresAsAnEmptySuccessfulResult() throws Exception {
        when(repository.findAllByUserIdAndStatus("owner", SubscriptionStatus.SUBSCRIBED))
                .thenThrow(new DataAccessResourceFailureException("private database details"));
        mvc.perform(get("/v1/subscriptions").header("Authorization", "Bearer token"))
                .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("private database details"))));
    }
}
