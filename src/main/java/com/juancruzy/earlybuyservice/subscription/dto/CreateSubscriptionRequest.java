package com.juancruzy.earlybuyservice.subscription.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record CreateSubscriptionRequest(
        @Schema(example = "507f1f77bcf86cd799439011", requiredMode = Schema.RequiredMode.REQUIRED)
        String articleId,
        @Schema(allowableValues = {"EMAIL", "SMS", "PUSH"}, requiredMode = Schema.RequiredMode.REQUIRED)
        String channel,
        @Schema(description = "Correo, teléfono internacional o identificador PUSH según el canal",
                example = "usuario@correo.com", requiredMode = Schema.RequiredMode.REQUIRED)
        String contact) {
}
