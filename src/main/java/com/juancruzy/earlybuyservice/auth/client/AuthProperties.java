package com.juancruzy.earlybuyservice.auth.client;

import java.net.URI;
import java.time.Duration;
import java.util.Objects;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("earlybuy.auth")
public record AuthProperties(URI baseUrl, Duration connectTimeout, Duration readTimeout) {

    public AuthProperties {
        Objects.requireNonNull(baseUrl, "Auth base URL is required");
        Objects.requireNonNull(connectTimeout, "Auth connect timeout is required");
        Objects.requireNonNull(readTimeout, "Auth read timeout is required");
        if (!("http".equalsIgnoreCase(baseUrl.getScheme()) || "https".equalsIgnoreCase(baseUrl.getScheme()))
                || baseUrl.getHost() == null) {
            throw new IllegalArgumentException("Auth base URL must be an absolute HTTP or HTTPS URL");
        }
        if (connectTimeout.isNegative() || connectTimeout.isZero()
                || readTimeout.isNegative() || readTimeout.isZero()) {
            throw new IllegalArgumentException("Auth timeouts must be greater than zero");
        }
    }
}
