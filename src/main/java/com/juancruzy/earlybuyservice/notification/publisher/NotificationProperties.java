package com.juancruzy.earlybuyservice.notification.publisher;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("earlybuy.notification")
public record NotificationProperties(Duration confirmationTimeout) {
    public NotificationProperties {
        if (confirmationTimeout == null || confirmationTimeout.toMillis() < 1) {
            throw new IllegalArgumentException("Notification confirmation timeout must be at least 1 ms");
        }
    }
}
