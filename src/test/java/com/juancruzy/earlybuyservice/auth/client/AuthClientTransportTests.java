package com.juancruzy.earlybuyservice.auth.client;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.juancruzy.earlybuyservice.auth.exception.AuthUnavailableException;
import com.juancruzy.earlybuyservice.shared.config.AuthClientConfig;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

class AuthClientTransportTests {

    private HttpServer server;
    private ExecutorService executor;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        executor = Executors.newVirtualThreadPerTaskExecutor();
        server.setExecutor(executor);
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
        executor.shutdownNow();
    }

    @Test
    void doesNotFollowRedirectsWithBearerCredentials() {
        var redirectedRequests = new AtomicInteger();
        server.createContext("/users/current", exchange -> {
            exchange.getResponseHeaders().set("Location", baseUrl() + "/redirected");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        server.createContext("/redirected", exchange -> {
            redirectedRequests.incrementAndGet();
            respondWithUser(exchange);
        });

        assertThatThrownBy(() -> client(Duration.ofSeconds(2)).currentUser("example-token"))
                .isInstanceOf(AuthUnavailableException.class);
        assertThat(redirectedRequests).hasValue(0);
    }

    @Test
    void enforcesConfiguredResponseTimeout() {
        var releaseResponse = new CountDownLatch(1);
        server.createContext("/users/current", exchange -> {
            try {
                if (releaseResponse.await(5, TimeUnit.SECONDS)) {
                    respondWithUser(exchange);
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });

        try {
            AuthClient client = client(Duration.ofMillis(200));
            assertTimeoutPreemptively(Duration.ofSeconds(3), () ->
                    assertThatThrownBy(() -> client.currentUser("example-token"))
                            .isInstanceOf(AuthUnavailableException.class));
        } finally {
            releaseResponse.countDown();
        }
    }

    private AuthClient client(Duration readTimeout) {
        var properties = new AuthProperties(URI.create(baseUrl()), Duration.ofSeconds(1), readTimeout);
        return new AuthClient(new AuthClientConfig().authRestClient(RestClient.builder(), properties));
    }

    private String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private void respondWithUser(HttpExchange exchange) throws IOException {
        byte[] body = "{\"id\":\"user-1\",\"enabled\":true,\"permissions\":[\"user\"]}"
                .getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }
}
