package com.juancruzy.earlybuyservice.catalog.dto;

public record CatalogArticleStatus(String articleId, boolean valid, int stock) {
}
