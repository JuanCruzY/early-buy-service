package com.juancruzy.earlybuyservice.subscription.repository;

import java.util.List;

import com.juancruzy.earlybuyservice.subscription.model.Subscription;
import com.juancruzy.earlybuyservice.subscription.model.SubscriptionStatus;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface SubscriptionRepository extends MongoRepository<Subscription, String> {

    List<Subscription> findAllByUserIdAndStatus(String userId, SubscriptionStatus status);

    List<Subscription> findAllByArticleIdAndStatus(String articleId, SubscriptionStatus status);

    boolean existsByUserIdAndArticleIdAndStatus(String userId, String articleId,
                                               SubscriptionStatus status);
}
