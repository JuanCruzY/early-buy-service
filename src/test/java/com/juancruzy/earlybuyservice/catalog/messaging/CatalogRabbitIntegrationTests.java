package com.juancruzy.earlybuyservice.catalog.messaging;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.juancruzy.earlybuyservice.catalog.dto.ArticleValidationRequest;
import com.juancruzy.earlybuyservice.catalog.dto.CatalogMessage;
import com.juancruzy.earlybuyservice.catalog.exception.CatalogTimeoutException;
import com.juancruzy.earlybuyservice.catalog.exception.CatalogUnavailableException;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.GetResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.core.type.TypeReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

@Testcontainers
class CatalogRabbitIntegrationTests extends CatalogRabbitTestSupport {

    private static final String ARTICLE = "507f1f77bcf86cd799439011";
    private static final String QUEUE = "catalog_article_exist";

    @Container
    static final GenericContainer<?> RABBIT = new GenericContainer<>("rabbitmq:3.13.6-management")
            .withExposedPorts(5672)
            .waitingFor(Wait.forLogMessage(".*Server startup complete.*", 1));

    private Channel channel;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("test.rabbit.host", RABBIT::getHost);
        registry.add("test.rabbit.port", () -> RABBIT.getMappedPort(5672));
    }

    @BeforeEach
    void prepareCatalogQueue() {
        admin.declareQueue(new Queue(QUEUE, false, false, false));
        admin.declareBinding(BindingBuilder.bind(new Queue(QUEUE, false, false, false))
                .to(new DirectExchange("article_exist", false, false)).with("article_exist"));
        channel = connectionFactory.createConnection().createChannel(false);
    }

    @AfterEach
    void cleanup() throws Exception {
        admin.deleteQueue(QUEUE);
        channel.close();
        await().atMost(Duration.ofSeconds(1)).untilAsserted(() -> assertThat(pending.size()).isZero());
    }

    @Test
    void exchangesLegacyJsonWithoutAmqpReplyHeaders() throws Exception {
        var received = new CompletableFuture<CatalogMessage<ArticleValidationRequest>>();
        channel.basicConsume(QUEUE, true, (tag, delivery) -> {
            var request = read(delivery.getBody());
            received.complete(request);
            reply(request, true, 0);
        }, tag -> { });

        assertThat(validation.validateForSubscription(ARTICLE).stock()).isZero();
        var request = received.get(1, TimeUnit.SECONDS);
        assertThat(request.routingKey()).isEqualTo(replyQueue.getName());
        assertThat(request.correlationId()).isEqualTo(request.message().referenceId());
        assertThat(replyQueue.isExclusive()).isTrue();
        assertThat(replyQueue.isAutoDelete()).isTrue();
    }

    @Test
    void correlatesConcurrentQueriesAnsweredInReverseOrder() throws Exception {
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = executor.submit(() -> client.getArticleStatus(ARTICLE));
            var second = executor.submit(() -> client.getArticleStatus("507f1f77bcf86cd799439012"));
            var requests = new java.util.ArrayList<CatalogMessage<ArticleValidationRequest>>();
            await().atMost(Duration.ofSeconds(1)).until(() -> {
                GetResponse message = channel.basicGet(QUEUE, true);
                if (message != null) {
                    requests.add(read(message.getBody()));
                }
                return requests.size() == 2;
            });
            for (int i = 1; i >= 0; i--) {
                var request = requests.get(i);
                reply(request, true, request.message().articleId().equals(ARTICLE) ? 0 : 25);
            }

            assertThat(first.get(1, TimeUnit.SECONDS).stock()).isZero();
            assertThat(second.get(1, TimeUnit.SECONDS).stock()).isEqualTo(25);
        }
    }

    @Test
    void timesOutWhenCatalogDoesNotConsumeTheQuery() {
        assertThatThrownBy(() -> client.getArticleStatus(ARTICLE)).isInstanceOf(CatalogTimeoutException.class);
    }

    @Test
    void detectsUnroutableQueriesUsingPublisherReturns() {
        admin.deleteQueue(QUEUE);
        assertThatThrownBy(() -> client.getArticleStatus(ARTICLE)).isInstanceOf(CatalogUnavailableException.class);
    }

    @Test
    void rejectsCorrelatedResponsesWithoutStock() throws Exception {
        channel.basicConsume(QUEUE, true, (tag, delivery) -> {
            var request = read(delivery.getBody());
            byte[] body = mapper.writeValueAsBytes(new CatalogMessage<>(request.correlationId(), "", "",
                    Map.of("articleId", request.message().articleId(),
                            "referenceId", request.message().referenceId(), "valid", true)));
            channel.basicPublish(request.exchange(), request.routingKey(), null, body);
        }, tag -> { });

        assertThatThrownBy(() -> client.getArticleStatus(ARTICLE)).isInstanceOf(CatalogUnavailableException.class);
    }

    private CatalogMessage<ArticleValidationRequest> read(byte[] body) {
        return mapper.readValue(body, new TypeReference<>() { });
    }

    private void reply(CatalogMessage<ArticleValidationRequest> request, boolean valid, int stock) throws IOException {
        var response = new CatalogMessage<>(request.correlationId(), "", "", Map.of(
                "articleId", request.message().articleId(), "referenceId", request.message().referenceId(),
                "valid", valid, "stock", stock, "price", 15.5));
        channel.basicPublish(request.exchange(), request.routingKey(), null, mapper.writeValueAsBytes(response));
    }
}
