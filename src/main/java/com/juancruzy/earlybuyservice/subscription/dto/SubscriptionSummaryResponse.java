package com.juancruzy.earlybuyservice.subscription.dto;

import com.juancruzy.earlybuyservice.subscription.model.NotificationChannel;
import com.juancruzy.earlybuyservice.subscription.model.Subscription;

public record SubscriptionSummaryResponse(String subscriptionId, String articleId,
                                           NotificationChannel channel, String contact) {
    public static SubscriptionSummaryResponse from(Subscription subscription) {
        return new SubscriptionSummaryResponse(subscription.getSubscriptionId(), subscription.getArticleId(),
                subscription.getChannel(), subscription.getContact());
    }
}
