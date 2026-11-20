package com.juancruzy.earlybuyservice.notification.service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import com.juancruzy.earlybuyservice.catalog.dto.StockChangedEvent;
import com.juancruzy.earlybuyservice.catalog.service.CatalogValidationService;
import com.juancruzy.earlybuyservice.notification.publisher.NotificationPublisher;
import com.juancruzy.earlybuyservice.shared.exception.ApplicationException;
import com.juancruzy.earlybuyservice.shared.exception.ErrorCode;
import com.juancruzy.earlybuyservice.subscription.model.NotificationChannel;
import com.juancruzy.earlybuyservice.subscription.model.Subscription;
import com.juancruzy.earlybuyservice.subscription.model.SubscriptionStatus;
import com.juancruzy.earlybuyservice.subscription.repository.SubscriptionRepository;
import com.juancruzy.earlybuyservice.subscription.service.SubscriptionOperationLock;
import com.juancruzy.earlybuyservice.subscription.service.SubscriptionService;
import com.juancruzy.earlybuyservice.subscription.validation.ContactValidator;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StockNotificationConcurrencyTests {
    @Test
    void cancellationCannotPassAnInFlightPublicationForTheSameSubscription() throws Exception {
        String articleId = "507f1f77bcf86cd799439011";
        String id = "507f1f77bcf86cd799439012";
        var repository = mock(SubscriptionRepository.class);
        var publisher = mock(NotificationPublisher.class);
        var locks = new SubscriptionOperationLock();
        var notificationService = new StockNotificationService(repository, locks, publisher);
        var subscriptionService = new SubscriptionService(repository, mock(CatalogValidationService.class),
                mock(ContactValidator.class), locks);
        var subscribed = subscription(articleId, id, SubscriptionStatus.SUBSCRIBED);
        var notified = subscription(articleId, id, SubscriptionStatus.NOTIFIED);
        var wasNotified = new AtomicBoolean();
        when(repository.findAllByArticleIdAndStatus(articleId, SubscriptionStatus.SUBSCRIBED))
                .thenReturn(List.of(subscribed));
        when(repository.findById(id)).thenAnswer(call -> Optional.of(wasNotified.get() ? notified : subscribed));
        when(repository.markNotifiedIfSubscribed(eq(id), any())).thenAnswer(call -> {
            wasNotified.set(true);
            return 1L;
        });
        var publishing = new CountDownLatch(1);
        var confirmation = new CompletableFuture<Void>();
        when(publisher.publish(eq(NotificationChannel.EMAIL), any(), eq("stock-event"), eq(id))).thenAnswer(call -> {
            publishing.countDown();
            return confirmation;
        });

        try (var executor = Executors.newFixedThreadPool(2)) {
            try {
                var sending = executor.submit(() -> notificationService.notifySubscribers(
                        new StockChangedEvent(UUID.randomUUID(), articleId, 25, Instant.now()), "stock-event"));
                assertThat(publishing.await(5, TimeUnit.SECONDS)).isTrue();
                var cancellation = executor.submit(() -> {
                    try {
                        subscriptionService.cancel("owner", id);
                        return null;
                    } catch (ApplicationException failure) {
                        return failure.getErrorCode();
                    }
                });
                org.junit.jupiter.api.Assertions.assertThrows(java.util.concurrent.TimeoutException.class,
                        () -> cancellation.get(150, TimeUnit.MILLISECONDS));
                confirmation.complete(null);
                sending.get(5, TimeUnit.SECONDS);
                assertThat(cancellation.get(5, TimeUnit.SECONDS)).isEqualTo(ErrorCode.INVALID_STATUS_TRANSITION);
                verify(repository, never()).cancelIfSubscribed(eq(id), eq("owner"), any());
            } finally {
                confirmation.completeExceptionally(new IllegalStateException("Test ended"));
            }
        }
    }

    private Subscription subscription(String articleId, String id, SubscriptionStatus state) {
        var subscription = new Subscription(articleId, "owner", "owner@example.com", NotificationChannel.EMAIL);
        ReflectionTestUtils.setField(subscription, "subscriptionId", id);
        ReflectionTestUtils.setField(subscription, "status", state);
        return subscription;
    }
}
