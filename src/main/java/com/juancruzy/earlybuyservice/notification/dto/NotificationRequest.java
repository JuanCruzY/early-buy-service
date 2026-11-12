package com.juancruzy.earlybuyservice.notification.dto;

public record NotificationRequest(String articleId, String userId, int currentStock, String contact) {
}
