package com.juancruzy.earlybuyservice.catalog.messaging;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.Executor;

import com.juancruzy.earlybuyservice.catalog.client.CatalogClient;
import com.juancruzy.earlybuyservice.catalog.client.CatalogProperties;
import com.juancruzy.earlybuyservice.catalog.dto.ArticleValidationRequest;
import com.juancruzy.earlybuyservice.catalog.dto.ArticleValidationResponse;
import com.juancruzy.earlybuyservice.catalog.dto.CatalogArticleStatus;
import com.juancruzy.earlybuyservice.catalog.dto.CatalogMessage;
import com.juancruzy.earlybuyservice.catalog.exception.CatalogTimeoutException;
import com.juancruzy.earlybuyservice.catalog.exception.CatalogUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

class CatalogMessagingTests {

    private static final String ARTICLE = "507f1f77bcf86cd799439011";
    private final JsonMapper mapper = JsonMapper.builder().build();
    private RabbitTemplate template;
    private PendingCatalogQueries pending;
    private CatalogReplyListener listener;
    private CatalogClient client;

    @BeforeEach
    void setup() {
        setup(Duration.ofSeconds(1), Runnable::run);
    }

    private void setup(Duration timeout, Executor executor) {
        template = mock(RabbitTemplate.class);
        pending = new PendingCatalogQueries(new CatalogProperties(timeout, 4));
        listener = new CatalogReplyListener(mapper, pending);
        var queue = new com.juancruzy.earlybuyservice.shared.config.CatalogRabbitConfig().catalogReplyQueue();
        client = new CatalogClient(pending, new CatalogPublisher(template, queue, mapper, pending), executor);
    }

    @Test
    void usesGoEnvelopeAndRegistersBeforeAnImmediateReply() {
        doAnswer(invocation -> {
            Message sent = invocation.getArgument(2);
            CatalogMessage<ArticleValidationRequest> request = readRequest(sent);
            assertThat(request.exchange()).isEqualTo("article_exist");
            assertThat(request.routingKey()).startsWith("earlybuy.article.validation.");
            assertThat(request.correlationId()).isEqualTo(request.message().referenceId());
            assertThat(request.message().articleId()).isEqualTo(ARTICLE);
            listener.receive(reply(request.message().referenceId(), ARTICLE, true, 0));
            return null;
        }).when(template).send(eq("article_exist"), eq("article_exist"), any(Message.class), any(CorrelationData.class));

        assertThat(client.getArticleStatus(ARTICLE)).isEqualTo(new CatalogArticleStatus(ARTICLE, true, 0));
        assertThat(pending.size()).isZero();
    }

    @Test
    void ignoresDuplicateAndUnknownReplies() {
        var query = pending.register(ARTICLE);
        listener.receive(reply("unknown", ARTICLE, true, 50));
        assertThat(query.result()).isNotDone();
        listener.receive(reply(query.referenceId(), ARTICLE, true, 0));
        listener.receive(reply(query.referenceId(), ARTICLE, true, 50));

        assertThat(query.result()).isCompletedWithValue(new CatalogArticleStatus(ARTICLE, true, 0));
        assertThat(pending.size()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"\"valid\":null,\"stock\":0", "\"valid\":true",
            "\"valid\":true,\"stock\":null", "\"stock\":0",
            "\"valid\":true,\"stock\":\"0\"", "\"valid\":true,\"stock\":0.5"})
    void rejectsMissingOrIncorrectResponseFields(String fields) {
        var query = pending.register(ARTICLE);
        listener.receive(raw("{\"message\":{\"referenceId\":\"" + query.referenceId()
                + "\",\"articleId\":\"" + ARTICLE + "\"," + fields + "}}"));

        assertThat(query.result()).isCompletedExceptionally();
        assertThatThrownBy(query.result()::join).hasCauseInstanceOf(CatalogUnavailableException.class);
        assertThat(pending.size()).isZero();
    }

    @Test
    void rejectsAResponseForTheWrongArticle() {
        var query = pending.register(ARTICLE);
        listener.receive(reply(query.referenceId(), "different-article", true, 0));

        assertThatThrownBy(query.result()::join).hasCauseInstanceOf(CatalogUnavailableException.class);
        assertThat(pending.size()).isZero();
    }

    @Test
    void discardsUncorrelatableMalformedMessagesWithoutRequeueing() {
        listener.receive(raw("{"));
        listener.receive(raw("null"));
        listener.receive(raw("{\"message\":{\"valid\":true,\"stock\":0}}"));
        assertThat(pending.size()).isZero();
    }

    @Test
    void timesOutAndCleansPendingEvenIfPublishingHasNotStarted() {
        setup(Duration.ofMillis(30), task -> { });

        assertTimeoutPreemptively(Duration.ofSeconds(2), () ->
                assertThatThrownBy(() -> client.getArticleStatus(ARTICLE))
                        .isInstanceOf(CatalogTimeoutException.class));
        assertThat(pending.size()).isZero();
    }

    @Test
    void lateReplyCannotSucceedAfterTheDeadline() {
        var registry = new PendingCatalogQueries(new CatalogProperties(Duration.ofNanos(1), 1));
        var query = registry.register(ARTICLE);
        registry.complete(new ArticleValidationResponse(ARTICLE, query.referenceId(), true, 0));

        assertThatThrownBy(query.result()::join).hasCauseInstanceOf(CatalogTimeoutException.class);
        assertThat(registry.size()).isZero();
    }

    @Test
    void translatesPublishingFailureAndCleansPending() {
        doThrow(new AmqpException("Simulated failure")).when(template)
                .send(eq("article_exist"), eq("article_exist"), any(Message.class), any(CorrelationData.class));

        assertThatThrownBy(() -> client.getArticleStatus(ARTICLE)).isInstanceOf(CatalogUnavailableException.class);
        assertThat(pending.size()).isZero();
    }

    @Test
    void translatesBrokerNack() {
        doAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(3);
            correlation.getFuture().complete(new CorrelationData.Confirm(false, "nack"));
            return null;
        }).when(template).send(eq("article_exist"), eq("article_exist"), any(Message.class), any(CorrelationData.class));

        assertThatThrownBy(() -> client.getArticleStatus(ARTICLE)).isInstanceOf(CatalogUnavailableException.class);
        assertThat(pending.size()).isZero();
    }

    @Test
    void rejectsAnUnroutableMessageEvenIfTheBrokerAcknowledgesIt() {
        doAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(3);
            correlation.setReturned(new ReturnedMessage(invocation.getArgument(2), 312,
                    "NO_ROUTE", "article_exist", "article_exist"));
            correlation.getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(template).send(eq("article_exist"), eq("article_exist"), any(Message.class), any(CorrelationData.class));

        assertThatThrownBy(() -> client.getArticleStatus(ARTICLE)).isInstanceOf(CatalogUnavailableException.class);
        assertThat(pending.size()).isZero();
    }

    @Test
    void shutdownReleasesWaitersAndRejectsNewQueries() {
        var query = pending.register(ARTICLE);
        pending.close();

        assertThatThrownBy(query.result()::join).hasCauseInstanceOf(CatalogUnavailableException.class);
        assertThatThrownBy(() -> pending.register(ARTICLE)).isInstanceOf(CatalogUnavailableException.class);
        assertThat(pending.size()).isZero();
    }

    @Test
    void boundsPendingRequestsAndReleasesCapacityAfterCompletion() {
        var registry = new PendingCatalogQueries(new CatalogProperties(Duration.ofSeconds(1), 1));
        var first = registry.register(ARTICLE);
        assertThatThrownBy(() -> registry.register(ARTICLE)).isInstanceOf(CatalogUnavailableException.class);
        registry.complete(new ArticleValidationResponse(ARTICLE, first.referenceId(), true, 0));
        var next = registry.register(ARTICLE);
        assertThat(next.referenceId()).isNotEqualTo(first.referenceId());
        registry.close();
        assertThat(registry.size()).isZero();
    }

    @Test
    void interruptedRequestPreservesTheInterruptAndCleansPending() {
        Thread.currentThread().interrupt();
        try {
            assertThatThrownBy(() -> client.getArticleStatus(ARTICLE)).isInstanceOf(CatalogUnavailableException.class);
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
            assertThat(pending.size()).isZero();
        } finally {
            Thread.interrupted();
        }
    }

    private CatalogMessage<ArticleValidationRequest> readRequest(Message message) {
        return mapper.readValue(message.getBody(), new TypeReference<>() { });
    }

    private Message reply(String reference, String article, boolean valid, int stock) {
        var envelope = new CatalogMessage<>(reference, "", "", Map.of(
                "referenceId", reference, "articleId", article, "valid", valid, "stock", stock, "price", 15.5));
        return new Message(mapper.writeValueAsBytes(envelope), new MessageProperties());
    }

    private Message raw(String body) {
        return new Message(body.getBytes(StandardCharsets.UTF_8), new MessageProperties());
    }
}
