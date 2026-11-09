package com.juancruzy.earlybuyservice.catalog.client;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.juancruzy.earlybuyservice.catalog.dto.CatalogArticleStatus;
import com.juancruzy.earlybuyservice.catalog.exception.CatalogTimeoutException;
import com.juancruzy.earlybuyservice.catalog.exception.CatalogUnavailableException;
import com.juancruzy.earlybuyservice.catalog.messaging.CatalogPublisher;
import com.juancruzy.earlybuyservice.catalog.messaging.PendingCatalogQueries;
import com.juancruzy.earlybuyservice.shared.exception.ApplicationException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
public class CatalogClient {

    private final PendingCatalogQueries pending;
    private final CatalogPublisher publisher;
    private final Executor publisherExecutor;

    public CatalogClient(PendingCatalogQueries pending, CatalogPublisher publisher,
                         @Qualifier("catalogPublisherExecutor") Executor publisherExecutor) {
        this.pending = pending;
        this.publisher = publisher;
        this.publisherExecutor = publisherExecutor;
    }

    public CatalogArticleStatus getArticleStatus(String articleId) {
        var query = pending.register(articleId);
        try {
            publisherExecutor.execute(() -> publisher.publish(query));
            return query.result().get(query.remainingNanos(), TimeUnit.NANOSECONDS);
        } catch (TimeoutException exception) {
            var timeout = new CatalogTimeoutException();
            query.result().completeExceptionally(timeout);
            throw timeout;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new CatalogUnavailableException(exception);
        } catch (RejectedExecutionException exception) {
            throw new CatalogUnavailableException(exception);
        } catch (ExecutionException exception) {
            if (exception.getCause() instanceof ApplicationException applicationException) {
                throw applicationException;
            }
            throw new CatalogUnavailableException(exception.getCause());
        } finally {
            if (!query.result().isDone()) {
                query.result().completeExceptionally(new CatalogUnavailableException());
            }
        }
    }
}
