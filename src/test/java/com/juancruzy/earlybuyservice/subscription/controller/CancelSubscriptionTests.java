package com.juancruzy.earlybuyservice.subscription.controller;

import java.util.List;
import java.util.Optional;

import com.juancruzy.earlybuyservice.auth.client.AuthClient;
import com.juancruzy.earlybuyservice.auth.dto.AuthUserResponse;
import com.juancruzy.earlybuyservice.auth.security.AuthAuthenticationProvider;
import com.juancruzy.earlybuyservice.auth.security.SecurityErrorHandler;
import com.juancruzy.earlybuyservice.catalog.service.CatalogValidationService;
import com.juancruzy.earlybuyservice.shared.config.SecurityConfig;
import com.juancruzy.earlybuyservice.subscription.model.NotificationChannel;
import com.juancruzy.earlybuyservice.subscription.model.Subscription;
import com.juancruzy.earlybuyservice.subscription.model.SubscriptionStatus;
import com.juancruzy.earlybuyservice.subscription.repository.SubscriptionRepository;
import com.juancruzy.earlybuyservice.subscription.service.SubscriptionService;
import com.juancruzy.earlybuyservice.subscription.validation.ContactValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(SubscriptionController.class)
@Import({SecurityConfig.class, AuthAuthenticationProvider.class, SecurityErrorHandler.class,
        SubscriptionService.class, ContactValidator.class})
class CancelSubscriptionTests {
    private static final String ID = "507f1f77bcf86cd799439012";
    @Autowired MockMvc mvc;
    @MockitoBean AuthClient auth;
    @MockitoBean CatalogValidationService catalog;
    @MockitoBean SubscriptionRepository repository;

    @BeforeEach
    void setUp() {
        when(auth.currentUser("token")).thenReturn(new AuthUserResponse("owner", List.of("user"), true));
    }

    @Test
    void cancelsWithAuthenticatedOwnerAndReturnsNoBody() throws Exception {
        when(repository.findById(ID)).thenReturn(Optional.of(subscription("owner", SubscriptionStatus.SUBSCRIBED)));
        when(repository.cancelIfSubscribed(eq(ID), eq("owner"), any())).thenReturn(true);
        mvc.perform(delete("/v1/subscriptions/" + ID.toUpperCase()).header("Authorization", "Bearer token"))
                .andExpect(status().isNoContent()).andExpect(content().string(""));
        verify(repository).cancelIfSubscribed(eq(ID), eq("owner"), notNull());
        verify(repository, never()).save(any(Subscription.class));
        verifyNoInteractions(catalog);
    }

    @Test
    void rejectsMissingCredentialsAndPermission() throws Exception {
        mvc.perform(delete("/v1/subscriptions/" + ID)).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("MISSING_JWT"));
        when(auth.currentUser("token")).thenReturn(new AuthUserResponse("owner", List.of("admin"), true));
        mvc.perform(delete("/v1/subscriptions/" + ID).header("Authorization", "Bearer token"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        verifyNoInteractions(repository);
    }

    @Test
    void rejectsMalformedOrMissingSubscription() throws Exception {
        mvc.perform(delete("/v1/subscriptions/invalid").header("Authorization", "Bearer token"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("SUBSCRIPTION_NOT_FOUND"));
        verifyNoInteractions(repository);
        when(repository.findById(ID)).thenReturn(Optional.empty());
        expectError(404, "SUBSCRIPTION_NOT_FOUND");
    }

    @Test
    void checksOwnershipBeforeStateAndDoesNotUpdateAnotherUsersSubscription() throws Exception {
        when(repository.findById(ID)).thenReturn(Optional.of(subscription("someone-else", SubscriptionStatus.NOTIFIED)));
        expectError(403, "NOT_OWNER");
        verify(repository, never()).cancelIfSubscribed(anyString(), anyString(), any());
    }

    @ParameterizedTest
    @EnumSource(value = SubscriptionStatus.class, names = {"CANCELLED", "NOTIFIED"})
    void rejectsTerminalStates(SubscriptionStatus state) throws Exception {
        when(repository.findById(ID)).thenReturn(Optional.of(subscription("owner", state)));
        expectError(409, "INVALID_STATUS_TRANSITION");
        verify(repository, never()).cancelIfSubscribed(anyString(), anyString(), any());
    }

    @Test
    void rereadsAfterConditionalUpdateLosesToNotification() throws Exception {
        when(repository.findById(ID)).thenReturn(Optional.of(subscription("owner", SubscriptionStatus.SUBSCRIBED)),
                Optional.of(subscription("owner", SubscriptionStatus.NOTIFIED)));
        expectError(409, "INVALID_STATUS_TRANSITION");
        verify(repository, times(2)).findById(ID);
    }

    @Test
    void rereadsAfterConditionalUpdateLosesToDeletion() throws Exception {
        when(repository.findById(ID)).thenReturn(Optional.of(subscription("owner", SubscriptionStatus.SUBSCRIBED)),
                Optional.empty());
        expectError(404, "SUBSCRIPTION_NOT_FOUND");
    }

    private void expectError(int status, String code) throws Exception {
        mvc.perform(delete("/v1/subscriptions/" + ID).header("Authorization", "Bearer token"))
                .andExpect(status().is(status)).andExpect(jsonPath("$.code").value(code));
    }

    private Subscription subscription(String owner, SubscriptionStatus state) {
        var subscription = new Subscription("article", owner, "user@example.com", NotificationChannel.EMAIL);
        ReflectionTestUtils.setField(subscription, "subscriptionId", ID);
        ReflectionTestUtils.setField(subscription, "status", state);
        return subscription;
    }
}
