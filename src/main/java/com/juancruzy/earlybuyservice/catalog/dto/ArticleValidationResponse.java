package com.juancruzy.earlybuyservice.catalog.dto;

public record ArticleValidationResponse(String articleId, String referenceId, Boolean valid, Integer stock) {
}
