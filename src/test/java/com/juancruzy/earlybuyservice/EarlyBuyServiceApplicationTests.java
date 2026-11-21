package com.juancruzy.earlybuyservice;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import com.juancruzy.earlybuyservice.catalog.dto.StockChangedEvent;
import com.juancruzy.earlybuyservice.notification.dto.NotificationRequest;
import com.juancruzy.earlybuyservice.notification.publisher.NotificationPublisher;
import com.juancruzy.earlybuyservice.notification.publisher.NotificationPublishException;
import com.juancruzy.earlybuyservice.subscription.model.NotificationChannel;
import com.juancruzy.earlybuyservice.subscription.model.Subscription;
import com.juancruzy.earlybuyservice.subscription.model.SubscriptionStatus;
import com.juancruzy.earlybuyservice.subscription.repository.SubscriptionRepository;
import com.juancruzy.earlybuyservice.subscription.service.SubscriptionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mongodb.MongoDBContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import tools.jackson.databind.ObjectMapper;
import org.bson.types.ObjectId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class EarlyBuyServiceApplicationTests {

    @Container
    static final MongoDBContainer MONGO = new MongoDBContainer("mongo:6.0");

    @Container
    static final GenericContainer<?> RABBIT = new GenericContainer<>("rabbitmq:3.13.6-management")
            .withExposedPorts(5672)
            .waitingFor(Wait.forLogMessage(".*Server startup complete.*", 1));

    @Autowired
    private MockMvc mockMvc;

    @Autowired private NotificationPublisher notificationPublisher;
    @Autowired private AmqpAdmin amqpAdmin;
    @Autowired private DirectExchange notificationExchange;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private SubscriptionRepository subscriptions;
    @Autowired private SubscriptionService subscriptionService;
    @Autowired @Qualifier("notificationRabbitTemplate")
    private RabbitTemplate notificationRabbitTemplate;

    @DynamicPropertySource
    static void configureMongo(DynamicPropertyRegistry registry) {
        registry.add("spring.mongodb.uri", () -> "mongodb://" + MONGO.getHost()
                + ":" + MONGO.getMappedPort(27017) + "/earlybuy_application_test");
        registry.add("spring.rabbitmq.host", RABBIT::getHost);
        registry.add("spring.rabbitmq.port", () -> RABBIT.getMappedPort(5672));
        registry.add("spring.rabbitmq.username", () -> "guest");
        registry.add("spring.rabbitmq.password", () -> "guest");
        registry.add("spring.rabbitmq.virtual-host", () -> "/");
    }

    @Test
    void contextLoads() {
    }

    @Test
    void swaggerDescribesBearerAuthenticationAndIsPubliclyAccessible() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.type").value("http"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
                .andExpect(jsonPath("$.paths['/v1/subscriptions'].post.responses['201'].content['application/json'].example.subscriptionId").exists())
                .andExpect(jsonPath("$.paths['/v1/subscriptions'].post.responses['503'].content['application/json'].examples.TIMEOUT_CATALOG.value.code").value("TIMEOUT_CATALOG"))
                .andExpect(jsonPath("$.paths['/v1/subscriptions/{subscriptionId}'].delete.responses['403'].content['application/json'].examples.NOT_OWNER.value.code").value("NOT_OWNER"))
                .andExpect(jsonPath("$.paths['/v1/subscriptions'].post.responses['400'].content['application/json'].examples.MISSING_FIELDS.value.code").value("MISSING_FIELDS"))
                .andExpect(jsonPath("$.paths['/v1/subscriptions'].post.requestBody.required").value(true))
                .andExpect(jsonPath("$.paths['/v1/subscriptions'].get.responses['200'].content['application/json'].schema.type").value("array"))
                .andExpect(jsonPath("$.paths['/v1/subscriptions/{subscriptionId}'].delete.responses['204'].content").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.ApiError.properties.code").exists());
    }

    @Test
    void subscriptionRoutesRequireCredentialsBeforeTheirControllersExist() throws Exception {
        mockMvc.perform(get("/v1/subscriptions"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("MISSING_JWT"));
    }

    @ParameterizedTest
    @CsvSource({"EMAIL,mail.notify", "SMS,sms.notify", "PUSH,push.notify"})
    void routesNotificationsWithTheExpectedJsonAndCorrelationId(NotificationChannel channel, String routingKey)
            throws Exception {
        var queue = bindNotificationQueue(routingKey);
        try {
            notificationPublisher.publish(channel, new NotificationRequest("article", "user", 25, "test-contact"),
                    "stock-event-1", "subscription-1").get(10, TimeUnit.SECONDS);
            var delivery = notificationRabbitTemplate.receive(queue.getName(), 2000);
            assertThat(delivery).isNotNull();
            var metadata = delivery.getMessageProperties();
            assertThat(metadata.getReceivedRoutingKey()).isEqualTo(routingKey);
            assertThat(metadata.getCorrelationId()).isEqualTo("stock-event-1");
            assertThat(metadata.getMessageId()).isEqualTo("subscription-1");
            assertThat(metadata.getReceivedDeliveryMode()).isEqualTo(MessageDeliveryMode.PERSISTENT);
            assertThat(metadata.getContentType()).isEqualTo("application/json");
            assertThat(objectMapper.readTree(delivery.getBody())).isEqualTo(objectMapper.readTree("""
                    {"articleId":"article","userId":"user","currentStock":25,"contact":"test-contact"}
                    """));
        } finally {
            amqpAdmin.deleteQueue(queue.getName());
        }
    }

    @Test
    void detectsAnUnroutableNotificationEvenWhenAnotherChannelHasAQueue() {
        var queue = bindNotificationQueue("mail.notify");
        try {
            var publication = notificationPublisher.publish(NotificationChannel.SMS,
                    new NotificationRequest("article", "user", 25, "+5491123456789"), "stock-event-1", "subscription-1");
            assertThatThrownBy(() -> publication.get(10, TimeUnit.SECONDS))
                    .hasCauseInstanceOf(NotificationPublishException.class)
                    .hasMessageContaining("no pudo enrutar");
            assertThat(notificationRabbitTemplate.receive(queue.getName())).isNull();
        } finally {
            amqpAdmin.deleteQueue(queue.getName());
        }
    }

    private Queue bindNotificationQueue(String routingKey) {
        // Timed receives create and cancel a consumer. Keep the queue for subsequent receives.
        var queue = new Queue("test.notifications." + UUID.randomUUID(), false, true, false);
        amqpAdmin.declareQueue(queue);
        amqpAdmin.declareBinding(BindingBuilder.bind(queue).to(notificationExchange).with(routingKey));
        return queue;
    }

    @Test
    void consumesStockEventsWithoutNotifyingCancelledOrAlreadyNotifiedSubscriptions() {
        String articleId = new ObjectId().toHexString();
        var active = subscriptions.insert(new Subscription(articleId, "active", "active@example.com",
                NotificationChannel.EMAIL));
        var cancelled = subscriptions.insert(new Subscription(articleId, "cancelled", "cancelled@example.com",
                NotificationChannel.EMAIL));
        subscriptionService.cancel(cancelled.getUserId(), cancelled.getSubscriptionId());
        var mail = bindNotificationQueue("mail.notify");
        try {
            sendStockEvent(new StockChangedEvent(UUID.randomUUID(), articleId, 0, Instant.now()), "no-stock");
            var replenishment = new StockChangedEvent(UUID.randomUUID(), articleId, 25, Instant.now());
            sendStockEvent(replenishment, "restock");
            var first = notificationRabbitTemplate.receive(mail.getName(), 8000);
            assertThat(first).isNotNull();
            assertThat(first.getMessageProperties().getMessageId()).isEqualTo(active.getSubscriptionId());
            assertThat(first.getMessageProperties().getCorrelationId()).isEqualTo("restock");
            assertThat(objectMapper.readTree(first.getBody())).isEqualTo(objectMapper.readTree("""
                    {"articleId":"%s","userId":"active","currentStock":25,"contact":"active@example.com"}
                    """.formatted(articleId)));
            assertNotified(active);

            sendStockEvent(replenishment, "restock");
            // A later event for another article proves that the duplicate was processed and acknowledged.
            String nextArticle = new ObjectId().toHexString();
            var next = subscriptions.insert(new Subscription(nextArticle, "next", "next@example.com",
                    NotificationChannel.EMAIL));
            var nextEvent = new StockChangedEvent(UUID.randomUUID(), nextArticle, 5, Instant.now());
            sendStockEvent(nextEvent, null);
            var second = notificationRabbitTemplate.receive(mail.getName(), 8000);
            assertThat(second).isNotNull();
            assertThat(second.getMessageProperties().getMessageId()).isEqualTo(next.getSubscriptionId());
            assertThat(second.getMessageProperties().getCorrelationId()).isEqualTo(nextEvent.eventId().toString());
            assertNotified(next);
            assertThat(subscriptions.findById(cancelled.getSubscriptionId()).orElseThrow().getStatus())
                    .isEqualTo(SubscriptionStatus.CANCELLED);
        } finally {
            amqpAdmin.deleteQueue(mail.getName());
        }
    }

    @Test
    void retriesFailedChannelWithoutSendingAgainToConfirmedSubscribers() {
        String articleId = new ObjectId().toHexString();
        var email = subscriptions.insert(new Subscription(articleId, "email", "email@example.com",
                NotificationChannel.EMAIL));
        var phone = subscriptions.insert(new Subscription(articleId, "phone", "+5491123456789",
                NotificationChannel.SMS));
        var mail = bindNotificationQueue("mail.notify");
        try {
            sendStockEvent(new StockChangedEvent(UUID.randomUUID(), articleId, 25, Instant.now()), "partial");
            var first = notificationRabbitTemplate.receive(mail.getName(), 8000);
            assertThat(first).isNotNull();
            assertThat(first.getMessageProperties().getMessageId()).isEqualTo(email.getSubscriptionId());
            assertNotified(email);
            assertThat(subscriptions.findById(phone.getSubscriptionId()).orElseThrow().getStatus())
                    .isEqualTo(SubscriptionStatus.SUBSCRIBED);

            var sms = bindNotificationQueue("sms.notify");
            try {
                var recovered = notificationRabbitTemplate.receive(sms.getName(), 15000);
                assertThat(recovered).isNotNull();
                assertThat(recovered.getMessageProperties().getMessageId()).isEqualTo(phone.getSubscriptionId());
                assertThat(recovered.getMessageProperties().getCorrelationId()).isEqualTo("partial");
                assertNotified(phone);
                assertThat(notificationRabbitTemplate.receive(mail.getName())).isNull();
            } finally {
                amqpAdmin.deleteQueue(sms.getName());
            }
        } finally {
            amqpAdmin.deleteQueue(mail.getName());
        }
    }

    private void sendStockEvent(StockChangedEvent event, String correlationId) {
        var properties = new MessageProperties();
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        properties.setCorrelationId(correlationId);
        notificationRabbitTemplate.send(StockChangedEvent.EXCHANGE, StockChangedEvent.ROUTING_KEY,
                new Message(objectMapper.writeValueAsBytes(event), properties));
    }

    private void assertNotified(Subscription subscription) {
        await().atMost(Duration.ofSeconds(8)).untilAsserted(() -> {
            var saved = subscriptions.findById(subscription.getSubscriptionId()).orElseThrow();
            assertThat(saved.getStatus()).isEqualTo(SubscriptionStatus.NOTIFIED);
            assertThat(saved.getNotifiedAt()).isNotNull();
        });
    }

}
