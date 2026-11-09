package com.juancruzy.earlybuyservice.catalog.messaging;

import java.util.Map;

import com.juancruzy.earlybuyservice.catalog.dto.ArticleValidationResponse;
import com.juancruzy.earlybuyservice.catalog.dto.CatalogMessage;
import com.juancruzy.earlybuyservice.catalog.exception.CatalogUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Component
public class CatalogReplyListener {

    private static final Logger log = LoggerFactory.getLogger(CatalogReplyListener.class);
    private static final TypeReference<CatalogMessage<Map<String, Object>>> RESPONSE_TYPE = new TypeReference<>() { };

    private final ObjectMapper objectMapper;
    private final PendingCatalogQueries pending;

    public CatalogReplyListener(ObjectMapper objectMapper, PendingCatalogQueries pending) {
        this.objectMapper = objectMapper;
        this.pending = pending;
    }

    @RabbitListener(id = "catalog-validation-replies", queues = "#{@catalogReplyQueue.name}")
    public void receive(Message message) {
        try {
            CatalogMessage<Map<String, Object>> envelope = objectMapper.readValue(message.getBody(), RESPONSE_TYPE);
            if (envelope == null || envelope.message() == null
                    || !(envelope.message().get("referenceId") instanceof String referenceId)
                    || referenceId.isBlank()) {
                log.warn("Respuesta de Catalog sin identificador de consulta");
                return;
            }
            Map<String, Object> body = envelope.message();
            if (!(body.get("articleId") instanceof String articleId)
                    || !(body.get("valid") instanceof Boolean valid)
                    || !(body.get("stock") instanceof Integer stock)) {
                pending.fail(referenceId, new CatalogUnavailableException());
                return;
            }
            pending.complete(new ArticleValidationResponse(articleId, referenceId, valid, stock));
        } catch (Exception exception) {
            // A reply without usable correlation cannot complete any pending request.
            log.warn("Respuesta de Catalog con formato inválido ({})", exception.getClass().getSimpleName());
        }
    }
}
