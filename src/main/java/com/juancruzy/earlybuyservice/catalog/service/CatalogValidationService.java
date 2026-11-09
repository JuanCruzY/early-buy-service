package com.juancruzy.earlybuyservice.catalog.service;

import com.juancruzy.earlybuyservice.catalog.client.CatalogClient;
import com.juancruzy.earlybuyservice.catalog.dto.CatalogArticleStatus;
import com.juancruzy.earlybuyservice.shared.exception.ApplicationException;
import com.juancruzy.earlybuyservice.shared.exception.ErrorCode;
import org.bson.types.ObjectId;
import org.springframework.stereotype.Service;

@Service
public class CatalogValidationService {

    private final CatalogClient catalogClient;

    public CatalogValidationService(CatalogClient catalogClient) {
        this.catalogClient = catalogClient;
    }

    public CatalogArticleStatus validateForSubscription(String articleId) {
        if (articleId == null || !ObjectId.isValid(articleId)) {
            throw new ApplicationException(ErrorCode.INVALID_ARTICLE_ID);
        }
        String normalizedId = new ObjectId(articleId).toHexString();
        CatalogArticleStatus article = catalogClient.getArticleStatus(normalizedId);
        if (!article.valid()) {
            throw new ApplicationException(ErrorCode.ARTICLE_NOT_FOUND);
        }
        if (article.stock() > 0) {
            throw new ApplicationException(ErrorCode.ARTICLE_HAS_STOCK);
        }
        return article;
    }
}
