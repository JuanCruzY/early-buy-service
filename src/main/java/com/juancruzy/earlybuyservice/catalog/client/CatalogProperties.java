package com.juancruzy.earlybuyservice.catalog.client;

import java.time.Duration;
import java.util.Objects;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("earlybuy.catalog")
public record CatalogProperties(Duration validationTimeout, int maxPendingRequests) {

    public CatalogProperties {
        Objects.requireNonNull(validationTimeout, "Catalog validation timeout is required");
        if (validationTimeout.isNegative() || validationTimeout.isZero() || maxPendingRequests < 1) {
            throw new IllegalArgumentException("Catalog timeout and maximum pending requests must be positive");
        }
        validationTimeout.toNanos();
    }
}
