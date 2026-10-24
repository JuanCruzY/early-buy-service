package com.juancruzy.earlybuyservice.auth.client;

import java.net.ConnectException;
import java.net.SocketTimeoutException;

import com.juancruzy.earlybuyservice.auth.exception.AuthUnavailableException;
import com.juancruzy.earlybuyservice.auth.exception.InvalidJwtException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class AuthClientTests {

    private MockRestServiceServer server;
    private AuthClient client;

    @BeforeEach
    void configureClient() {
        var builder = RestClient.builder().baseUrl("http://auth.test");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new AuthClient(builder.build());
    }

    @AfterEach
    void verifyRequests() {
        server.verify();
    }

    @Test
    void forwardsBearerAndReadsRealAuthResponseFields() {
        server.expect(requestTo("http://auth.test/users/current"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer example-token"))
                .andRespond(withSuccess("""
                        {"id":"507f1f77bcf86cd799439011","name":"Example",
                         "login":"example","permissions":["user","admin"],"enabled":true}
                        """, MediaType.APPLICATION_JSON));

        var user = client.currentUser("example-token");

        assertThat(user.id()).isEqualTo("507f1f77bcf86cd799439011");
        assertThat(user.permissions()).containsExactly("user", "admin");
        assertThat(user.enabled()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(ints = {401, 404})
    void rejectsTokensThatAuthDoesNotRecognizeAsAnActiveIdentity(int status) {
        server.expect(requestTo("http://auth.test/users/current"))
                .andRespond(withStatus(HttpStatusCode.valueOf(status)));

        assertThatThrownBy(() -> client.currentUser("example-token"))
                .isInstanceOf(InvalidJwtException.class);
    }

    @ParameterizedTest
    @ValueSource(ints = {204, 302, 400, 403, 429, 500, 503})
    void treatsUnexpectedUpstreamStatusesAsUnavailable(int status) {
        server.expect(requestTo("http://auth.test/users/current"))
                .andRespond(withStatus(HttpStatusCode.valueOf(status)));

        assertThatThrownBy(() -> client.currentUser("example-token"))
                .isInstanceOf(AuthUnavailableException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "{", "null", "{}",
            "{\"id\":\"\",\"enabled\":true}",
            "{\"id\":\"user-1\"}",
            "{\"id\":\"user-1\",\"enabled\":true,\"permissions\":[null]}",
            "{\"id\":\"user-1\",\"enabled\":true,\"permissions\":[\"\"]}"})
    void rejectsUnusableAuthResponses(String body) {
        server.expect(requestTo("http://auth.test/users/current"))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.currentUser("example-token"))
                .isInstanceOf(AuthUnavailableException.class);
    }

    @Test
    void translatesTimeoutWithoutAcceptingTheRequest() {
        server.expect(requestTo("http://auth.test/users/current"))
                .andRespond(withException(new SocketTimeoutException("Simulated timeout")));

        assertThatThrownBy(() -> client.currentUser("example-token"))
                .isInstanceOf(AuthUnavailableException.class);
    }

    @Test
    void translatesConnectionFailureWithoutAcceptingTheRequest() {
        server.expect(requestTo("http://auth.test/users/current"))
                .andRespond(withException(new ConnectException("Simulated connection failure")));

        assertThatThrownBy(() -> client.currentUser("example-token"))
                .isInstanceOf(AuthUnavailableException.class);
    }
}
