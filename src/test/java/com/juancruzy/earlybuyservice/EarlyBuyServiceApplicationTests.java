package com.juancruzy.earlybuyservice;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mongodb.MongoDBContainer;

@SpringBootTest
@Testcontainers
class EarlyBuyServiceApplicationTests {

    @Container
    static final MongoDBContainer MONGO = new MongoDBContainer("mongo:6.0");

    @DynamicPropertySource
    static void configureMongo(DynamicPropertyRegistry registry) {
        registry.add("spring.mongodb.uri", () -> "mongodb://" + MONGO.getHost()
                + ":" + MONGO.getMappedPort(27017) + "/earlybuy_application_test");
    }

    @Test
    void contextLoads() {
    }

}
