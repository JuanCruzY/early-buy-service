package com.juancruzy.earlybuyservice.catalog.dto;

import java.time.Instant;
import java.util.UUID;

public record StockChangedEvent(UUID eventId, String articleId, Integer currentStock, Instant updatedAt) {
    public static final String EXCHANGE = "catalog-events";
    public static final String ROUTING_KEY = "catalog.stock_changed";
    public static final String QUEUE = "earlybuy.catalog.stock_changed";
}
