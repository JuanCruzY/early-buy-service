package com.juancruzy.earlybuyservice.subscription.repository;

import java.time.Instant;

import com.juancruzy.earlybuyservice.subscription.model.NotificationChannel;
import com.juancruzy.earlybuyservice.subscription.model.Subscription;
import com.juancruzy.earlybuyservice.subscription.model.SubscriptionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.mongodb.test.autoconfigure.DataMongoTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mongodb.MongoDBContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataMongoTest
@Testcontainers
class SubscriptionRepositoryTests {

    @Container
    static final MongoDBContainer MONGO = new MongoDBContainer("mongo:6.0");

    @DynamicPropertySource
    static void configureMongo(DynamicPropertyRegistry registry) {
        registry.add("spring.mongodb.uri", () -> "mongodb://" + MONGO.getHost()
                + ":" + MONGO.getMappedPort(27017) + "/earlybuy_repository_test");
    }

    @Autowired
    private SubscriptionRepository repository;

    @Autowired
    private MongoTemplate mongoTemplate;

    @BeforeEach
    void clearSubscriptions() {
        repository.deleteAll();
    }

    @Test
    void savesAndFindsSubscriptionByGeneratedId() {
        Subscription saved = repository.save(new Subscription(
                "article-1", "user-1", "user@example.com", NotificationChannel.EMAIL));
        String id = (String) ReflectionTestUtils.getField(saved, "subscriptionId");

        assertThat(id).isNotBlank();
        assertThat(repository.findById(id)).hasValueSatisfying(found ->
                assertThat(found).usingRecursiveComparison()
                        .withComparatorForType((left, right) ->
                                left.truncatedTo(java.time.temporal.ChronoUnit.MILLIS)
                                        .compareTo(right.truncatedTo(java.time.temporal.ChronoUnit.MILLIS)),
                                Instant.class)
                        .isEqualTo(saved));
    }

    @Test
    void rejectsDuplicateActiveSubscriptionEvenWithDifferentChannel() {
        repository.save(new Subscription(
                "article-1", "user-1", "user@example.com", NotificationChannel.EMAIL));

        assertThatThrownBy(() -> repository.save(new Subscription(
                "article-1", "user-1", "+5492611234567", NotificationChannel.SMS)))
                .isInstanceOf(DuplicateKeyException.class);
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void allowsDifferentUsersOrArticles() {
        repository.save(new Subscription(
                "article-1", "user-1", "one@example.com", NotificationChannel.EMAIL));
        repository.save(new Subscription(
                "article-1", "user-2", "two@example.com", NotificationChannel.EMAIL));
        repository.save(new Subscription(
                "article-2", "user-1", "one@example.com", NotificationChannel.EMAIL));

        assertThat(repository.count()).isEqualTo(3);
    }

    @Test
    void queriesOnlyMatchingUserArticleAndStatus() {
        repository.save(new Subscription(
                "article-1", "user-1", "one@example.com", NotificationChannel.EMAIL));
        repository.save(new Subscription(
                "article-2", "user-1", "one@example.com", NotificationChannel.EMAIL));
        repository.save(new Subscription(
                "article-1", "user-2", "two@example.com", NotificationChannel.EMAIL));
        mongoTemplate.updateMulti(Query.query(Criteria.where("articleId").is("article-2")),
                Update.update("status", SubscriptionStatus.CANCELLED), Subscription.class);

        assertThat(repository.findAllByUserIdAndStatus("user-1", SubscriptionStatus.SUBSCRIBED))
                .extracting("articleId").containsExactly("article-1");
        assertThat(repository.findAllByArticleIdAndStatus("article-1", SubscriptionStatus.SUBSCRIBED))
                .extracting("userId").containsExactlyInAnyOrder("user-1", "user-2");
        assertThat(repository.findAllByUserIdAndStatus("unknown", SubscriptionStatus.SUBSCRIBED))
                .isEmpty();
        assertThat(repository.existsByUserIdAndArticleIdAndStatus(
                "user-1", "article-1", SubscriptionStatus.SUBSCRIBED)).isTrue();
        assertThat(repository.existsByUserIdAndArticleIdAndStatus(
                "user-1", "article-2", SubscriptionStatus.SUBSCRIBED)).isFalse();
    }

    @ParameterizedTest
    @EnumSource(value = SubscriptionStatus.class, names = {"CANCELLED", "NOTIFIED"})
    void allowsResubscriptionAndMultipleHistoricalDocuments(SubscriptionStatus historicalStatus) {
        String timestampField = historicalStatus == SubscriptionStatus.CANCELLED
                ? "cancelledAt" : "notifiedAt";

        for (int i = 0; i < 2; i++) {
            repository.save(new Subscription(
                    "article-1", "user-1", "user@example.com", NotificationChannel.EMAIL));
            mongoTemplate.updateFirst(Query.query(Criteria.where("status").is(SubscriptionStatus.SUBSCRIBED)),
                    Update.update("status", historicalStatus).set(timestampField, Instant.now()),
                    Subscription.class);
        }
        repository.save(new Subscription(
                "article-1", "user-1", "user@example.com", NotificationChannel.EMAIL));

        assertThat(repository.count()).isEqualTo(3);
        assertThat(repository.findAllByUserIdAndStatus("user-1", SubscriptionStatus.SUBSCRIBED))
                .hasSize(1);
        assertThat(repository.findAllByUserIdAndStatus("user-1", historicalStatus))
                .hasSize(2)
                .allSatisfy(subscription -> assertThat(subscription)
                        .extracting("status", timestampField)
                        .doesNotContainNull());
    }
}
