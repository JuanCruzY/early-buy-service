package com.juancruzy.earlybuyservice.catalog.messaging;

import java.time.Duration;

import com.juancruzy.earlybuyservice.catalog.dto.StockChangedEvent;
import com.juancruzy.earlybuyservice.notification.service.StockNotificationService;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.ImmediateRequeueAmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
public class StockChangedListener {
    private static final Logger log = LoggerFactory.getLogger(StockChangedListener.class);
    private static final Duration RETRY_DELAY = Duration.ofSeconds(5);
    private final ObjectMapper mapper;
    private final StockNotificationService notifications;

    public StockChangedListener(ObjectMapper mapper, StockNotificationService notifications) {
        this.mapper = mapper;
        this.notifications = notifications;
    }

    @RabbitListener(queues = StockChangedEvent.QUEUE, containerFactory = "stockEventsListenerFactory")
    public void receive(Message message) {
        var metadata = message.getMessageProperties();
        // The fanout exchange delivers all events regardless of routing key.
        if (!StockChangedEvent.ROUTING_KEY.equals(metadata.getReceivedRoutingKey())) {
            return;
        }

        StockChangedEvent event;
        try {
            event = mapper.readValue(message.getBody(), StockChangedEvent.class);
        } catch (JacksonException error) {
            log.warn("Se omitió un evento de stock con JSON inválido");
            return;
        }
        if (event == null || event.eventId() == null || event.articleId() == null
                || !ObjectId.isValid(event.articleId()) || event.currentStock() == null || event.updatedAt() == null) {
            log.warn("Se omitió un evento de stock con campos inválidos o ausentes");
            return;
        }

        String correlationId = metadata.getCorrelationId();
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = event.eventId().toString();
        }
        try {
            notifications.notifySubscribers(event, correlationId);
        } catch (RuntimeException failure) {
            try {
                Thread.sleep(RETRY_DELAY);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
            throw new ImmediateRequeueAmqpException("Se reintentará el evento de stock " + event.eventId(), failure);
        }
    }
}
