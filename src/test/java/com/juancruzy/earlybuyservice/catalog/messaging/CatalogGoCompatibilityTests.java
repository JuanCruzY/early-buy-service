package com.juancruzy.earlybuyservice.catalog.messaging;

import java.time.Duration;
import java.util.List;

import com.juancruzy.earlybuyservice.shared.exception.ApplicationException;
import com.juancruzy.earlybuyservice.shared.exception.ErrorCode;
import com.mongodb.client.MongoClients;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mongodb.MongoDBContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

@Testcontainers
@EnabledIfEnvironmentVariable(named = "CATALOG_GO_IMAGE", matches = ".+")
class CatalogGoCompatibilityTests extends CatalogRabbitTestSupport {

    static final Network NETWORK = Network.newNetwork();

    @Container
    static final GenericContainer<?> RABBIT = new GenericContainer<>("rabbitmq:3.13.6-management")
            .withNetwork(NETWORK).withNetworkAliases("rabbit").withExposedPorts(5672);

    @Container
    static final MongoDBContainer MONGO = new MongoDBContainer("mongo:6.0")
            .withNetwork(NETWORK).withNetworkAliases("mongo");

    @Container
    static final GenericContainer<?> CATALOG = new GenericContainer<>(
            System.getenv().getOrDefault("CATALOG_GO_IMAGE", "prod-cataloggo-go"))
            .withNetwork(NETWORK).dependsOn(RABBIT, MONGO)
            .withEnv("MONGO_URL", "mongodb://mongo:27017")
            .withEnv("RABBIT_URL", "amqp://guest:guest@rabbit:5672/")
            .withEnv("FLUENT_URL", "localhost:24224")
            .withExposedPorts(3002);

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("test.rabbit.host", RABBIT::getHost);
        registry.add("test.rabbit.port", () -> RABBIT.getMappedPort(5672));
        registry.add("earlybuy.catalog.validation-timeout", () -> "5s");
    }

    @Test
    void validatesArticlesAgainstTheRealGoService() {
        await().atMost(Duration.ofSeconds(30)).until(() -> {
            var queue = admin.getQueueInfo("catalog_article_exist");
            return queue != null && queue.getConsumerCount() > 0;
        });

        ObjectId noStock = new ObjectId();
        ObjectId stocked = new ObjectId();
        ObjectId disabled = new ObjectId();
        try (var mongo = MongoClients.create("mongodb://" + MONGO.getHost() + ":" + MONGO.getMappedPort(27017))) {
            mongo.getDatabase("catalog").getCollection("catalog").insertMany(List.of(
                    article(noStock, true, 0), article(stocked, true, 25), article(disabled, false, 0)));

            assertThat(validation.validateForSubscription(noStock.toHexString()).stock()).isZero();
            assertCode(stocked, ErrorCode.ARTICLE_HAS_STOCK);
            assertCode(disabled, ErrorCode.ARTICLE_NOT_FOUND);
            assertCode(new ObjectId(), ErrorCode.ARTICLE_NOT_FOUND);
        }
        await().atMost(Duration.ofSeconds(1)).untilAsserted(() -> assertThat(pending.size()).isZero());
    }

    private Document article(ObjectId id, boolean enabled, int stock) {
        return new Document("_id", id).append("enabled", enabled).append("stock", stock).append("price", 15.5)
                .append("description", new Document("name", "Compatibility test").append("description", "Test"));
    }

    private void assertCode(ObjectId id, ErrorCode code) {
        assertThatThrownBy(() -> validation.validateForSubscription(id.toHexString()))
                .isInstanceOfSatisfying(ApplicationException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(code));
    }
}
