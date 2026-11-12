package com.juancruzy.earlybuyservice.notification.publisher;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.juancruzy.earlybuyservice.notification.dto.NotificationRequest;
import com.juancruzy.earlybuyservice.subscription.model.NotificationChannel;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class NotificationPublisher {
    public static final String EXCHANGE = "notifications";

    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;
    private final long confirmationTimeoutMillis;

    public NotificationPublisher(@Qualifier("notificationRabbitTemplate") RabbitTemplate rabbitTemplate,
                                 ObjectMapper objectMapper, NotificationProperties properties) {
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
        this.confirmationTimeoutMillis = properties.confirmationTimeout().toMillis();
    }

    public CompletableFuture<Void> publish(NotificationChannel channel, NotificationRequest notification,
                                           String correlationId) {
        Objects.requireNonNull(notification, "notification");
        if (correlationId == null || correlationId.isBlank()) {
            throw new IllegalArgumentException("correlationId is required");
        }

        String routingKey = switch (channel) {
            case EMAIL -> "mail.notify";
            case SMS -> "sms.notify";
            case PUSH -> "push.notify";
        };

        var properties = new MessageProperties();
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
        properties.setCorrelationId(correlationId);
        // Several notifications can share one event correlationId. Each publish needs its own confirm ID.
        var confirmation = new CorrelationData(UUID.randomUUID().toString());

        try {
            var message = new Message(objectMapper.writeValueAsBytes(notification), properties);
            rabbitTemplate.send(EXCHANGE, routingKey, message, confirmation);
        } catch (RuntimeException error) {
            return CompletableFuture.failedFuture(
                    new NotificationPublishException("No se pudo publicar la notificación en " + routingKey, error));
        }

        return confirmation.getFuture().copy()
                .orTimeout(confirmationTimeoutMillis, TimeUnit.MILLISECONDS)
                .handle((result, error) -> {
                    if (error instanceof TimeoutException) {
                        throw new NotificationPublishException(
                                "Venció la espera de confirmación para " + routingKey, error);
                    }
                    if (error != null) {
                        throw new NotificationPublishException(
                                "Falló la confirmación de la notificación en " + routingKey, error);
                    }
                    if (confirmation.getReturned() != null) {
                        throw new NotificationPublishException("RabbitMQ no pudo enrutar la notificación de " + routingKey);
                    }
                    if (!result.ack()) {
                        throw new NotificationPublishException("RabbitMQ rechazó la notificación de " + routingKey);
                    }
                    return null;
                });
    }
}
