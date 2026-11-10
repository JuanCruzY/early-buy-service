package com.juancruzy.earlybuyservice.subscription.service;

import java.util.List;
import java.time.Instant;

import com.juancruzy.earlybuyservice.catalog.service.CatalogValidationService;
import com.juancruzy.earlybuyservice.shared.exception.ApplicationException;
import com.juancruzy.earlybuyservice.shared.exception.ErrorCode;
import com.juancruzy.earlybuyservice.subscription.dto.CreateSubscriptionRequest;
import com.juancruzy.earlybuyservice.subscription.dto.SubscriptionResponse;
import com.juancruzy.earlybuyservice.subscription.dto.SubscriptionSummaryResponse;
import com.juancruzy.earlybuyservice.subscription.model.NotificationChannel;
import com.juancruzy.earlybuyservice.subscription.model.Subscription;
import com.juancruzy.earlybuyservice.subscription.model.SubscriptionStatus;
import com.juancruzy.earlybuyservice.subscription.repository.SubscriptionRepository;
import com.juancruzy.earlybuyservice.subscription.validation.ContactValidator;
import org.bson.types.ObjectId;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

@Service
public class SubscriptionService {
    private final SubscriptionRepository repository;
    private final CatalogValidationService catalog;
    private final ContactValidator contacts;

    public SubscriptionService(SubscriptionRepository repository, CatalogValidationService catalog,
                               ContactValidator contacts) {
        this.repository = repository;
        this.catalog = catalog;
        this.contacts = contacts;
    }

    public SubscriptionResponse subscribe(String userId, CreateSubscriptionRequest request) {
        if (request == null || missing(request.articleId()) || missing(request.channel())
                || missing(request.contact())) {
            throw new ApplicationException(ErrorCode.MISSING_FIELDS);
        }
        NotificationChannel channel;
        try {
            channel = NotificationChannel.valueOf(request.channel());
        } catch (IllegalArgumentException exception) {
            throw new ApplicationException(ErrorCode.INVALID_CHANNEL);
        }
        if (!ObjectId.isValid(request.articleId())) {
            throw new ApplicationException(ErrorCode.INVALID_ARTICLE_ID);
        }
        String articleId = new ObjectId(request.articleId()).toHexString();
        String contact = contacts.validateAndNormalize(channel, request.contact());
        if (repository.existsByUserIdAndArticleIdAndStatus(userId, articleId, SubscriptionStatus.SUBSCRIBED)) {
            throw new ApplicationException(ErrorCode.ALREADY_SUBSCRIBED);
        }
        catalog.validateForSubscription(articleId);
        var subscription = new Subscription(articleId, userId, contact, channel);
        try {
            return SubscriptionResponse.from(repository.insert(subscription));
        } catch (DuplicateKeyException exception) {
            if (exception.getMessage() != null && exception.getMessage().contains("unique_active_subscription")) {
                throw new ApplicationException(ErrorCode.ALREADY_SUBSCRIBED, exception);
            }
            throw exception;
        }
    }

    public List<SubscriptionSummaryResponse> findActiveSubscriptions(String userId) {
        return repository.findAllByUserIdAndStatus(userId, SubscriptionStatus.SUBSCRIBED)
                .stream()
                .map(SubscriptionSummaryResponse::from)
                .toList();
    }

    public void cancel(String userId, String subscriptionId) {
        if (subscriptionId == null || !ObjectId.isValid(subscriptionId)) {
            throw new ApplicationException(ErrorCode.SUBSCRIPTION_NOT_FOUND);
        }
        String normalizedId = new ObjectId(subscriptionId).toHexString();
        Subscription subscription = findOwnedSubscription(userId, normalizedId);
        subscription.cancel(Instant.now());
        if (!repository.cancelIfSubscribed(normalizedId, userId, subscription.getCancelledAt())) {
            // Re-read to distinguish deletion, ownership and state changes since the initial read.
            findOwnedSubscription(userId, normalizedId);
            throw new ApplicationException(ErrorCode.INVALID_STATUS_TRANSITION);
        }
    }

    private Subscription findOwnedSubscription(String userId, String subscriptionId) {
        Subscription subscription = repository.findById(subscriptionId)
                .orElseThrow(() -> new ApplicationException(ErrorCode.SUBSCRIPTION_NOT_FOUND));
        if (!subscription.getUserId().equals(userId)) {
            throw new ApplicationException(ErrorCode.NOT_OWNER);
        }
        return subscription;
    }

    private boolean missing(String value) {
        return value == null || value.isBlank();
    }
}
