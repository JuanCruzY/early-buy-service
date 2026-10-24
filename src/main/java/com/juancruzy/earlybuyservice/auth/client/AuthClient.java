package com.juancruzy.earlybuyservice.auth.client;

import com.juancruzy.earlybuyservice.auth.dto.AuthUserResponse;
import com.juancruzy.earlybuyservice.auth.exception.AuthUnavailableException;
import com.juancruzy.earlybuyservice.auth.exception.InvalidJwtException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class AuthClient {

    private final RestClient restClient;

    public AuthClient(@Qualifier("authRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    public AuthUserResponse currentUser(String token) {
        try {
            AuthUserResponse user = restClient.get()
                    .uri("/users/current")
                    .headers(headers -> headers.setBearerAuth(token))
                    .retrieve()
                    .onStatus(status -> status.value() != 200, (request, response) -> {
                        int status = response.getStatusCode().value();
                        if (status == 401 || status == 404) {
                            throw new InvalidJwtException();
                        }
                        throw new AuthUnavailableException();
                    })
                    .body(AuthUserResponse.class);

            if (user == null || user.id() == null || user.id().isBlank() || user.enabled() == null
                    || (user.permissions() != null && user.permissions().stream()
                            .anyMatch(permission -> permission == null || permission.isBlank()))) {
                throw new AuthUnavailableException();
            }
            return user;
        } catch (RestClientException exception) {
            throw new AuthUnavailableException(exception);
        }
    }
}
