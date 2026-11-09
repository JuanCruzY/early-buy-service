package com.juancruzy.earlybuyservice.subscription.controller;

import java.util.List;

import com.juancruzy.earlybuyservice.auth.client.AuthClient;
import com.juancruzy.earlybuyservice.auth.dto.AuthUserResponse;
import com.juancruzy.earlybuyservice.auth.security.AuthAuthenticationProvider;
import com.juancruzy.earlybuyservice.auth.security.SecurityErrorHandler;
import com.juancruzy.earlybuyservice.catalog.dto.CatalogArticleStatus;
import com.juancruzy.earlybuyservice.catalog.service.CatalogValidationService;
import com.juancruzy.earlybuyservice.shared.config.SecurityConfig;
import com.juancruzy.earlybuyservice.shared.exception.ApplicationException;
import com.juancruzy.earlybuyservice.shared.exception.ErrorCode;
import com.juancruzy.earlybuyservice.subscription.model.Subscription;
import com.juancruzy.earlybuyservice.subscription.model.SubscriptionStatus;
import com.juancruzy.earlybuyservice.subscription.repository.SubscriptionRepository;
import com.juancruzy.earlybuyservice.subscription.service.SubscriptionService;
import com.juancruzy.earlybuyservice.subscription.validation.ContactValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(SubscriptionController.class)
@Import({SecurityConfig.class, AuthAuthenticationProvider.class, SecurityErrorHandler.class,
        SubscriptionService.class, ContactValidator.class})
class CreateSubscriptionTests {
    private static final String ARTICLE = "507f1f77bcf86cd799439011";
    @Autowired MockMvc mvc;
    @MockitoBean AuthClient auth;
    @MockitoBean CatalogValidationService catalog;
    @MockitoBean SubscriptionRepository repository;

    @BeforeEach
    void setUp() {
        when(auth.currentUser("test-token")).thenReturn(new AuthUserResponse("real-user", List.of("user"), true));
        when(catalog.validateForSubscription(ARTICLE)).thenReturn(new CatalogArticleStatus(ARTICLE, true, 0));
        when(repository.insert(any(Subscription.class))).thenAnswer(call -> {
            Subscription subscription = call.getArgument(0);
            ReflectionTestUtils.setField(subscription, "subscriptionId", "generated-id");
            return subscription;
        });
    }

    @ParameterizedTest
    @CsvSource({"EMAIL,user@example.com,user@example.com", "SMS,+54 9 (11) 2345-6789,+5491123456789",
            "PUSH,opaque-device-token,opaque-device-token"})
    void createsSubscriptionWithAuthenticatedOwnerAndNormalizedContact(String channel, String contact, String expected)
            throws Exception {
        send("{\"articleId\":\"" + ARTICLE.toUpperCase() + "\",\"channel\":\"" + channel
                + "\",\"contact\":\"" + contact + "\",\"userId\":\"attacker\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.subscriptionId").value("generated-id"))
                .andExpect(jsonPath("$.articleId").value(ARTICLE))
                .andExpect(jsonPath("$.userId").value("real-user"))
                .andExpect(jsonPath("$.channel").value(channel))
                .andExpect(jsonPath("$.contact").value(expected))
                .andExpect(jsonPath("$.status").value("SUBSCRIBED"))
                .andExpect(jsonPath("$.createdAt").isString())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"cancelledAt\":null")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"notifiedAt\":null")));
        var order = inOrder(repository, catalog);
        order.verify(repository).existsByUserIdAndArticleIdAndStatus("real-user", ARTICLE, SubscriptionStatus.SUBSCRIBED);
        order.verify(catalog).validateForSubscription(ARTICLE);
        order.verify(repository).insert(any(Subscription.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "null", "", "{\"articleId\":\"x\"}",
            "{\"articleId\":\"x\",\"channel\":\"EMAIL\",\"contact\":\"  \"}"})
    void rejectsMissingFields(String json) throws Exception {
        send(json).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("MISSING_FIELDS"));
        verifyNoInteractions(repository, catalog);
    }

    @ParameterizedTest
    @CsvSource({"EMAIL,not-an-email", "SMS,1123456789", "SMS,+54 hola 123", "SMS,+999123456789",
            "SMS,+541", "SMS,+1 800 FLOWERS", "SMS,+5491123456789 ext 1"})
    void rejectsInvalidContacts(String channel, String contact) throws Exception {
        send(body(channel, contact)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_CONTACT"));
        verifyNoInteractions(repository, catalog);
    }

    @Test
    void rejectsInvalidChannelAndArticleId() throws Exception {
        send(body("email", "user@example.com")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_CHANNEL"));
        send(body("EMAIL", "user@example.com").replace(ARTICLE, "invalid"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_ARTICLE_ID"));
        verifyNoInteractions(repository, catalog);
    }

    @Test
    void rejectsMalformedJson() throws Exception {
        send("{").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        verifyNoInteractions(repository, catalog);
    }

    @Test
    void requiresCredentialsAndUserPermission() throws Exception {
        mvc.perform(post("/v1/subscriptions").contentType("application/json").content(body("EMAIL", "a@b.com")))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("MISSING_JWT"));
        when(auth.currentUser("test-token")).thenReturn(new AuthUserResponse("admin", List.of("admin"), true));
        send(body("EMAIL", "a@b.com")).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        verifyNoInteractions(repository, catalog);
    }

    @Test
    void rejectsExistingActiveSubscriptionBeforeCallingCatalog() throws Exception {
        when(repository.existsByUserIdAndArticleIdAndStatus("real-user", ARTICLE, SubscriptionStatus.SUBSCRIBED))
                .thenReturn(true);
        send(body("EMAIL", "a@b.com")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_SUBSCRIBED"));
        verifyNoInteractions(catalog);
        verify(repository, never()).insert(any(Subscription.class));
    }

    @Test
    void mapsConcurrentDuplicateButDoesNotHideOtherUniqueConstraintFailures() throws Exception {
        when(repository.insert(any(Subscription.class)))
                .thenThrow(new DuplicateKeyException("E11000 index: unique_active_subscription dup key"));
        send(body("EMAIL", "a@b.com")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_SUBSCRIBED"));
        when(repository.insert(any(Subscription.class))).thenThrow(new DuplicateKeyException("index: _id_"));
        send(body("EMAIL", "a@b.com")).andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));
    }

    @ParameterizedTest
    @CsvSource({"ARTICLE_NOT_FOUND,404", "ARTICLE_HAS_STOCK,409", "TIMEOUT_CATALOG,503", "CATALOG_UNAVAILABLE,503"})
    void doesNotSaveWhenCatalogFails(String code, int status) throws Exception {
        when(catalog.validateForSubscription(ARTICLE)).thenThrow(new ApplicationException(ErrorCode.valueOf(code)));
        send(body("EMAIL", "a@b.com")).andExpect(status().is(status)).andExpect(jsonPath("$.code").value(code));
        verify(repository, never()).insert(any(Subscription.class));
    }

    private ResultActions send(String body) throws Exception {
        return mvc.perform(post("/v1/subscriptions").header("Authorization", "Bearer test-token")
                .contentType("application/json").content(body));
    }

    private String body(String channel, String contact) {
        return "{\"articleId\":\"" + ARTICLE + "\",\"channel\":\"" + channel + "\",\"contact\":\"" + contact + "\"}";
    }
}
