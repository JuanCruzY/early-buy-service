package com.juancruzy.earlybuyservice;

import java.util.concurrent.TimeUnit;

import com.juancruzy.earlybuyservice.notification.dto.NotificationRequest;
import com.juancruzy.earlybuyservice.notification.publisher.NotificationPublisher;
import com.juancruzy.earlybuyservice.notification.publisher.NotificationPublishException;
import com.juancruzy.earlybuyservice.subscription.model.NotificationChannel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.AnonymousQueue;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.MessageDeliveryMode;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"));
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
                    "stock-event-1").get(10, TimeUnit.SECONDS);
            var delivery = notificationRabbitTemplate.receive(queue.getName(), 2000);
            assertThat(delivery).isNotNull();
            var metadata = delivery.getMessageProperties();
            assertThat(metadata.getReceivedRoutingKey()).isEqualTo(routingKey);
            assertThat(metadata.getCorrelationId()).isEqualTo("stock-event-1");
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
                    new NotificationRequest("article", "user", 25, "+5491123456789"), "stock-event-1");
            assertThatThrownBy(() -> publication.get(10, TimeUnit.SECONDS))
                    .hasCauseInstanceOf(NotificationPublishException.class)
                    .hasMessageContaining("no pudo enrutar");
            assertThat(notificationRabbitTemplate.receive(queue.getName())).isNull();
        } finally {
            amqpAdmin.deleteQueue(queue.getName());
        }
    }

    private AnonymousQueue bindNotificationQueue(String routingKey) {
        var queue = new AnonymousQueue();
        amqpAdmin.declareQueue(queue);
        amqpAdmin.declareBinding(BindingBuilder.bind(queue).to(notificationExchange).with(routingKey));
        return queue;
    }

}
