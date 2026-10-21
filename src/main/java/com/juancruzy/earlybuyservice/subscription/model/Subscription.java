package com.juancruzy.earlybuyservice.subscription.model;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "subscriptions")
public class Subscription {

    @Id
    private String subscriptionId;
    private String articleId;
    private String userId;
    private String contact;
    private NotificationChannel channel;
    private SubscriptionStatus status;
    private Instant createdAt;
    private Instant cancelledAt;
    private Instant notifiedAt;

    public Subscription(String articleId, String userId, String contact,
                        NotificationChannel channel) {
        this.articleId = articleId;
        this.userId = userId;
        this.contact = contact;
        this.channel = channel;
        this.status = SubscriptionStatus.SUBSCRIBED;
        this.createdAt = Instant.now();
    }
}
