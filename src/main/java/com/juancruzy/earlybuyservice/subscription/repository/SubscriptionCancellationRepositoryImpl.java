package com.juancruzy.earlybuyservice.subscription.repository;

import java.time.Instant;

import com.juancruzy.earlybuyservice.subscription.model.Subscription;
import com.juancruzy.earlybuyservice.subscription.model.SubscriptionStatus;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

public class SubscriptionCancellationRepositoryImpl implements SubscriptionCancellationRepository {
    private final MongoTemplate mongo;

    public SubscriptionCancellationRepositoryImpl(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    @Override
    public boolean cancelIfSubscribed(String subscriptionId, String userId, Instant cancelledAt) {
        var query = Query.query(Criteria.where("subscriptionId").is(subscriptionId)
                .and("userId").is(userId).and("status").is(SubscriptionStatus.SUBSCRIBED));
        var update = new Update().set("status", SubscriptionStatus.CANCELLED).set("cancelledAt", cancelledAt);
        return mongo.updateFirst(query, update, Subscription.class).getModifiedCount() == 1;
    }
}
