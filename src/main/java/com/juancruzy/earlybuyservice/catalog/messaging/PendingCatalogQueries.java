package com.juancruzy.earlybuyservice.catalog.messaging;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

import com.juancruzy.earlybuyservice.catalog.client.CatalogProperties;
import com.juancruzy.earlybuyservice.catalog.dto.ArticleValidationResponse;
import com.juancruzy.earlybuyservice.catalog.dto.CatalogArticleStatus;
import com.juancruzy.earlybuyservice.catalog.exception.CatalogTimeoutException;
import com.juancruzy.earlybuyservice.catalog.exception.CatalogUnavailableException;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

@Component
public class PendingCatalogQueries {

    private final ConcurrentHashMap<String, PendingQuery> queries = new ConcurrentHashMap<>();
    private final CatalogProperties properties;
    private final Semaphore capacity;
    private boolean closed;

    public PendingCatalogQueries(CatalogProperties properties) {
        this.properties = properties;
        this.capacity = new Semaphore(properties.maxPendingRequests());
    }

    public synchronized PendingQuery register(String articleId) {
        if (closed || !capacity.tryAcquire()) {
            throw new CatalogUnavailableException();
        }
        String referenceId = UUID.randomUUID().toString();
        var query = new PendingQuery(referenceId, articleId,
                System.nanoTime() + properties.validationTimeout().toNanos(), new CompletableFuture<>());
        queries.put(referenceId, query);
        query.result().whenComplete((result, error) -> {
            queries.remove(referenceId, query);
            capacity.release();
        });
        return query;
    }

    public void complete(ArticleValidationResponse response) {
        PendingQuery query = queries.get(response.referenceId());
        if (query == null) {
            return;
        }
        if (query.remainingNanos() == 0) {
            query.result().completeExceptionally(new CatalogTimeoutException());
        } else if (!query.articleId().equals(response.articleId())
                || response.valid() == null || response.stock() == null) {
            query.result().completeExceptionally(new CatalogUnavailableException());
        } else {
            query.result().complete(new CatalogArticleStatus(response.articleId(), response.valid(), response.stock()));
        }
    }

    public void fail(String referenceId, Throwable exception) {
        PendingQuery query = queries.get(referenceId);
        if (query != null) {
            query.result().completeExceptionally(exception);
        }
    }

    @PreDestroy
    public synchronized void close() {
        closed = true;
        queries.values().forEach(query -> query.result().completeExceptionally(new CatalogUnavailableException()));
    }

    int size() {
        return queries.size();
    }

    public record PendingQuery(String referenceId, String articleId, long deadlineNanos,
                               CompletableFuture<CatalogArticleStatus> result) {

        public long remainingNanos() {
            return Math.max(0, deadlineNanos - System.nanoTime());
        }
    }
}
