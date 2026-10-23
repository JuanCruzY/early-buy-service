package com.juancruzy.earlybuyservice.shared.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    MISSING_FIELDS(HttpStatus.BAD_REQUEST, "Faltan campos obligatorios"),
    INVALID_CHANNEL(HttpStatus.BAD_REQUEST, "El canal de notificación no es válido"),
    INVALID_ARTICLE_ID(HttpStatus.BAD_REQUEST, "El formato del identificador del artículo no es válido"),
    INVALID_CONTACT(HttpStatus.BAD_REQUEST, "El dato de contacto no es válido para el canal seleccionado"),
    MISSING_JWT(HttpStatus.UNAUTHORIZED, "Faltan credenciales"),
    INVALID_JWT(HttpStatus.UNAUTHORIZED, "El token es inválido o está expirado"),
    NOT_OWNER(HttpStatus.FORBIDDEN, "La suscripción no pertenece al usuario autenticado"),
    ARTICLE_NOT_FOUND(HttpStatus.NOT_FOUND, "El artículo no existe"),
    SUBSCRIPTION_NOT_FOUND(HttpStatus.NOT_FOUND, "La suscripción no existe"),
    ALREADY_SUBSCRIBED(HttpStatus.CONFLICT, "Ya estás suscripto a este artículo"),
    ARTICLE_HAS_STOCK(HttpStatus.CONFLICT, "El artículo ya tiene stock"),
    INVALID_STATUS_TRANSITION(HttpStatus.CONFLICT, "La suscripción no se encuentra en estado suscripto"),
    TIMEOUT_CATALOG(HttpStatus.SERVICE_UNAVAILABLE, "Catalog no respondió dentro del tiempo esperado"),
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "La solicitud no es válida"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Ocurrió un error interno en el servicio");

    private final HttpStatus status;
    private final String defaultMessage;

    ErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getDefaultMessage() {
        return defaultMessage;
    }
}
