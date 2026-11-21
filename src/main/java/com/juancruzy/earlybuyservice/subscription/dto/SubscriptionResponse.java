package com.juancruzy.earlybuyservice.subscription.dto;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import com.juancruzy.earlybuyservice.subscription.model.NotificationChannel;
import com.juancruzy.earlybuyservice.subscription.model.Subscription;
import com.juancruzy.earlybuyservice.subscription.model.SubscriptionStatus;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record SubscriptionResponse(String subscriptionId, String articleId, String userId,
                                   String contact, NotificationChannel channel, SubscriptionStatus status,
                                   Instant createdAt,
                                   @Schema(types = {"string", "null"}, format = "date-time") Instant cancelledAt,
                                   @Schema(types = {"string", "null"}, format = "date-time") Instant notifiedAt) {
    public static SubscriptionResponse from(Subscription subscription) {
        return new SubscriptionResponse(subscription.getSubscriptionId(), subscription.getArticleId(),
                subscription.getUserId(), subscription.getContact(), subscription.getChannel(),
                subscription.getStatus(), subscription.getCreatedAt(), subscription.getCancelledAt(),
                subscription.getNotifiedAt());
    }
}
