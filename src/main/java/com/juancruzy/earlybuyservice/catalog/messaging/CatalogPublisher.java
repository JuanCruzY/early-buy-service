package com.juancruzy.earlybuyservice.catalog.messaging;

import com.juancruzy.earlybuyservice.catalog.dto.ArticleValidationRequest;
import com.juancruzy.earlybuyservice.catalog.dto.CatalogMessage;
import com.juancruzy.earlybuyservice.catalog.exception.CatalogUnavailableException;
import org.springframework.amqp.core.AnonymousQueue;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class CatalogPublisher {

    public static final String EXCHANGE = "article_exist";
    public static final String REQUEST_ROUTING_KEY = "article_exist";

    private final RabbitTemplate rabbitTemplate;
    private final AnonymousQueue replyQueue;
    private final ObjectMapper objectMapper;
    private final PendingCatalogQueries pending;

    public CatalogPublisher(@Qualifier("catalogRabbitTemplate") RabbitTemplate rabbitTemplate,
                            @Qualifier("catalogReplyQueue") AnonymousQueue replyQueue,
                            ObjectMapper objectMapper, PendingCatalogQueries pending) {
        this.rabbitTemplate = rabbitTemplate;
        this.replyQueue = replyQueue;
        this.objectMapper = objectMapper;
        this.pending = pending;
    }

    public void publish(PendingCatalogQueries.PendingQuery query) {
        if (query.result().isDone()) {
            return;
        }
        try {
            var envelope = new CatalogMessage<>(query.referenceId(), EXCHANGE, replyQueue.getName(),
                    new ArticleValidationRequest(query.referenceId(), query.articleId()));
            var properties = new MessageProperties();
            properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
            properties.setExpiration(Long.toString(Math.max(1, query.remainingNanos() / 1_000_000)));
            var message = new Message(objectMapper.writeValueAsBytes(envelope), properties);
            var correlation = new CorrelationData(query.referenceId());
            correlation.getFuture().whenComplete((confirm, error) -> {
                if (error != null || !confirm.ack() || correlation.getReturned() != null) {
                    pending.fail(query.referenceId(), new CatalogUnavailableException(error));
                }
            });
            rabbitTemplate.send(EXCHANGE, REQUEST_ROUTING_KEY, message, correlation);
        } catch (Exception exception) {
            pending.fail(query.referenceId(), new CatalogUnavailableException(exception));
        }
    }
}
