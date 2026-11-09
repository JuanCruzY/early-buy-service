package com.juancruzy.earlybuyservice.subscription.service;

import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.List;

import com.juancruzy.earlybuyservice.catalog.dto.CatalogArticleStatus;
import com.juancruzy.earlybuyservice.catalog.service.CatalogValidationService;
import com.juancruzy.earlybuyservice.shared.exception.ApplicationException;
import com.juancruzy.earlybuyservice.shared.exception.ErrorCode;
import com.juancruzy.earlybuyservice.subscription.dto.CreateSubscriptionRequest;
import com.juancruzy.earlybuyservice.subscription.model.SubscriptionStatus;
import com.juancruzy.earlybuyservice.subscription.repository.SubscriptionRepository;
import com.juancruzy.earlybuyservice.subscription.validation.ContactValidator;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.mongodb.test.autoconfigure.DataMongoTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mongodb.MongoDBContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@DataMongoTest
@Import({SubscriptionService.class, ContactValidator.class, SubscriptionPersistenceTests.ValidationConfig.class})
@Testcontainers
class SubscriptionPersistenceTests {
    private static final String ARTICLE = "507f1f77bcf86cd799439011";
    @Container static final MongoDBContainer MONGO = new MongoDBContainer("mongo:6.0");
    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.mongodb.uri", () -> "mongodb://" + MONGO.getHost() + ":"
                + MONGO.getMappedPort(27017) + "/earlybuy_creation_test");
    }
    @TestConfiguration(proxyBeanMethods = false)
    static class ValidationConfig {
        @Bean Validator validator() { return Validation.buildDefaultValidatorFactory().getValidator(); }
    }
    @Autowired SubscriptionService service;
    @Autowired SubscriptionRepository repository;
    @Autowired MongoTemplate mongo;
    @MockitoBean CatalogValidationService catalog;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        when(catalog.validateForSubscription(anyString())).thenReturn(new CatalogArticleStatus(ARTICLE, true, 0));
    }

    @Test
    void persistsNormalizedContactAndAllowsResubscriptionAfterCancellation() {
        var first = service.subscribe("user", new CreateSubscriptionRequest(ARTICLE, "SMS", "+54 9 (11) 2345-6789"));
        assertThat(first.subscriptionId()).isNotBlank();
        assertThat(repository.findById(first.subscriptionId())).hasValueSatisfying(saved -> {
            assertThat(saved.getContact()).isEqualTo("+5491123456789");
            assertThat(saved.getStatus()).isEqualTo(SubscriptionStatus.SUBSCRIBED);
        });
        mongo.updateFirst(Query.query(Criteria.where("_id").is(first.subscriptionId())),
                Update.update("status", SubscriptionStatus.CANCELLED), "subscriptions");
        var second = service.subscribe("user", new CreateSubscriptionRequest(ARTICLE, "PUSH", "opaque-token"));
        assertThat(second.subscriptionId()).isNotEqualTo(first.subscriptionId());
        assertThat(repository.count()).isEqualTo(2);
    }

    @Test
    void concurrentRequestsCreateOneSubscriptionAndReturnOneBusinessConflict() throws Exception {
        var barrier = new CyclicBarrier(2);
        when(catalog.validateForSubscription(ARTICLE)).thenAnswer(call -> {
            barrier.await(10, TimeUnit.SECONDS);
            return new CatalogArticleStatus(ARTICLE, true, 0);
        });
        try (var executor = Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<String> subscribe = () -> {
                try {
                    service.subscribe("user", new CreateSubscriptionRequest(ARTICLE, "EMAIL", "user@example.com"));
                    return "CREATED";
                } catch (ApplicationException exception) {
                    return exception.getErrorCode().name();
                }
            };
            var first = executor.submit(subscribe);
            var second = executor.submit(subscribe);
            assertThat(List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder("CREATED", ErrorCode.ALREADY_SUBSCRIBED.name());
        }
        assertThat(repository.count()).isEqualTo(1);
    }
}
