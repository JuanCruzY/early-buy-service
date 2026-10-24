package com.juancruzy.earlybuyservice;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mongodb.MongoDBContainer;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class EarlyBuyServiceApplicationTests {

    @Container
    static final MongoDBContainer MONGO = new MongoDBContainer("mongo:6.0");

    @Autowired
    private MockMvc mockMvc;

    @DynamicPropertySource
    static void configureMongo(DynamicPropertyRegistry registry) {
        registry.add("spring.mongodb.uri", () -> "mongodb://" + MONGO.getHost()
                + ":" + MONGO.getMappedPort(27017) + "/earlybuy_application_test");
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

}
