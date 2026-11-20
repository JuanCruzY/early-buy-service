package com.juancruzy.earlybuyservice.subscription.repository;

import java.time.Instant;
import java.util.List;

import com.juancruzy.earlybuyservice.subscription.model.Subscription;
import com.juancruzy.earlybuyservice.subscription.model.SubscriptionStatus;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.Update;

public interface SubscriptionRepository extends MongoRepository<Subscription, String>, SubscriptionCancellationRepository {

    List<Subscription> findAllByUserIdAndStatus(String userId, SubscriptionStatus status);

    List<Subscription> findAllByArticleIdAndStatus(String articleId, SubscriptionStatus status);

    boolean existsByUserIdAndArticleIdAndStatus(String userId, String articleId,
                                               SubscriptionStatus status);

    @Query("{ 'subscriptionId': ?0, 'status': 'SUBSCRIBED' }")
    @Update("{ '$set': { 'status': 'NOTIFIED', 'notifiedAt': ?1 } }")
    long markNotifiedIfSubscribed(String subscriptionId, Instant notifiedAt);
}
