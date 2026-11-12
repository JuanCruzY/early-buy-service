package com.juancruzy.earlybuyservice.notification.publisher;

public class NotificationPublishException extends RuntimeException {
    public NotificationPublishException(String message) {
        super(message);
    }

    public NotificationPublishException(String message, Throwable cause) {
        super(message, cause);
    }
}
