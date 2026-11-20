package com.juancruzy.earlybuyservice.notification.service;

import java.time.Instant;
import java.util.concurrent.ExecutionException;

import com.juancruzy.earlybuyservice.catalog.dto.StockChangedEvent;
import com.juancruzy.earlybuyservice.notification.dto.NotificationRequest;
import com.juancruzy.earlybuyservice.notification.publisher.NotificationPublishException;
import com.juancruzy.earlybuyservice.notification.publisher.NotificationPublisher;
import com.juancruzy.earlybuyservice.subscription.model.SubscriptionStatus;
import com.juancruzy.earlybuyservice.subscription.repository.SubscriptionRepository;
import com.juancruzy.earlybuyservice.subscription.service.SubscriptionOperationLock;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class StockNotificationService {
    private static final Logger log = LoggerFactory.getLogger(StockNotificationService.class);
    private final SubscriptionRepository repository;
    private final SubscriptionOperationLock operations;
    private final NotificationPublisher publisher;

    public StockNotificationService(SubscriptionRepository repository, SubscriptionOperationLock operations,
                                    NotificationPublisher publisher) {
        this.repository = repository;
        this.operations = operations;
        this.publisher = publisher;
    }

    public void notifySubscribers(StockChangedEvent event, String correlationId) {
        if (event.currentStock() <= 0) {
            log.debug("Evento {} sin stock disponible", event.eventId());
            return;
        }
        String articleId = new ObjectId(event.articleId()).toHexString();
        var subscriptions = repository.findAllByArticleIdAndStatus(articleId, SubscriptionStatus.SUBSCRIBED);
        if (subscriptions.isEmpty()) {
            log.debug("Evento {} sin suscripciones activas", event.eventId());
            return;
        }

        NotificationPublishException pendingFailure = null;
        for (var subscription : subscriptions) {
            String id = subscription.getSubscriptionId();
            try {
                operations.run(id, () -> publishIfActive(id, event.currentStock(), correlationId));
            } catch (NotificationPublishException failure) {
                if (Thread.currentThread().isInterrupted()) {
                    throw failure;
                }
                // A missing route for one channel should not stop the other subscribers.
                pendingFailure = failure;
            }
        }
        if (pendingFailure != null) {
            throw pendingFailure;
        }
    }

    private void publishIfActive(String subscriptionId, int currentStock, String correlationId) {
        var subscription = repository.findById(subscriptionId).orElse(null);
        if (subscription == null || subscription.getStatus() != SubscriptionStatus.SUBSCRIBED) {
            return;
        }
        var request = new NotificationRequest(subscription.getArticleId(), subscription.getUserId(),
                currentStock, subscription.getContact());
        try {
            publisher.publish(subscription.getChannel(), request, correlationId, subscriptionId).get();
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new NotificationPublishException("Se interrumpió la espera de confirmación", error);
        } catch (ExecutionException error) {
            throw new NotificationPublishException("Falló la publicación para " + subscriptionId, error.getCause());
        }
        if (repository.markNotifiedIfSubscribed(subscriptionId, Instant.now()) != 1) {
            log.warn("La suscripción {} cambió de estado después de publicar su aviso", subscriptionId);
        }
    }
}
