package com.juancruzy.earlybuyservice.subscription.model;

import java.time.Instant;
import java.util.Objects;

import com.juancruzy.earlybuyservice.shared.exception.ApplicationException;
import com.juancruzy.earlybuyservice.shared.exception.ErrorCode;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "subscriptions")
@CompoundIndex(
        name = "unique_active_subscription",
        def = "{'userId': 1, 'articleId': 1}",
        unique = true,
        partialFilter = "{'status': 'SUBSCRIBED'}"
)
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

    public void cancel(Instant cancelledAt) {
        if (status != SubscriptionStatus.SUBSCRIBED) {
            throw new ApplicationException(ErrorCode.INVALID_STATUS_TRANSITION);
        }
        this.cancelledAt = Objects.requireNonNull(cancelledAt, "cancelledAt must not be null");
        this.status = SubscriptionStatus.CANCELLED;
    }

    public String getSubscriptionId() { return subscriptionId; }
    public String getArticleId() { return articleId; }
    public String getUserId() { return userId; }
    public String getContact() { return contact; }
    public NotificationChannel getChannel() { return channel; }
    public SubscriptionStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getCancelledAt() { return cancelledAt; }
    public Instant getNotifiedAt() { return notifiedAt; }
}
