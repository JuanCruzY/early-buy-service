package com.juancruzy.earlybuyservice.subscription.repository;

import java.time.Instant;

public interface SubscriptionCancellationRepository {
    boolean cancelIfSubscribed(String subscriptionId, String userId, Instant cancelledAt);
}
