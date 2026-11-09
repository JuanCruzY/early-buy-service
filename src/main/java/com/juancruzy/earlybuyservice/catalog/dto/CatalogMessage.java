package com.juancruzy.earlybuyservice.catalog.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CatalogMessage<T>(@JsonProperty("correlation_id") String correlationId,
                                String exchange, @JsonProperty("routing_key") String routingKey,
                                T message) {
}
