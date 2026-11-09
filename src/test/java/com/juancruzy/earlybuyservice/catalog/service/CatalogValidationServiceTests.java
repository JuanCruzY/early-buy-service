package com.juancruzy.earlybuyservice.catalog.service;

import com.juancruzy.earlybuyservice.catalog.client.CatalogClient;
import com.juancruzy.earlybuyservice.catalog.dto.CatalogArticleStatus;
import com.juancruzy.earlybuyservice.shared.exception.ApplicationException;
import com.juancruzy.earlybuyservice.shared.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CatalogValidationServiceTests {

    private static final String ARTICLE = "507f1f77bcf86cd799439011";
    private final CatalogClient client = mock(CatalogClient.class);
    private final CatalogValidationService service = new CatalogValidationService(client);

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"123", "507f1f77bcf86cd79943901z"})
    void rejectsInvalidIdsBeforePublishing(String articleId) {
        assertCode(() -> service.validateForSubscription(articleId), ErrorCode.INVALID_ARTICLE_ID);
        verifyNoInteractions(client);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void allowsEnabledArticlesWithoutStockAndNormalizesIds(int stock) {
        when(client.getArticleStatus(ARTICLE)).thenReturn(new CatalogArticleStatus(ARTICLE, true, stock));

        assertThat(service.validateForSubscription(ARTICLE.toUpperCase()).articleId()).isEqualTo(ARTICLE);
    }

    @Test
    void rejectsMissingOrDisabledArticlesBeforeConsideringStock() {
        when(client.getArticleStatus(ARTICLE)).thenReturn(new CatalogArticleStatus(ARTICLE, false, 25));
        assertCode(() -> service.validateForSubscription(ARTICLE), ErrorCode.ARTICLE_NOT_FOUND);
    }

    @Test
    void rejectsArticlesWithStock() {
        when(client.getArticleStatus(ARTICLE)).thenReturn(new CatalogArticleStatus(ARTICLE, true, 25));
        assertCode(() -> service.validateForSubscription(ARTICLE), ErrorCode.ARTICLE_HAS_STOCK);
    }

    private void assertCode(Runnable action, ErrorCode code) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(ApplicationException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(code));
    }
}
