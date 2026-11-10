package com.juancruzy.earlybuyservice.subscription.controller;

import java.util.List;

import com.juancruzy.earlybuyservice.auth.security.AuthenticatedUser;
import com.juancruzy.earlybuyservice.subscription.dto.CreateSubscriptionRequest;
import com.juancruzy.earlybuyservice.subscription.dto.SubscriptionResponse;
import com.juancruzy.earlybuyservice.subscription.dto.SubscriptionSummaryResponse;
import com.juancruzy.earlybuyservice.subscription.service.SubscriptionService;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/subscriptions")
public class SubscriptionController {
    private final SubscriptionService service;

    public SubscriptionController(SubscriptionService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Suscribirse a un artículo sin stock")
    public SubscriptionResponse subscribe(@AuthenticationPrincipal AuthenticatedUser user,
                                          @RequestBody(required = false) CreateSubscriptionRequest request) {
        return service.subscribe(user.id(), request);
    }

    @GetMapping
    @Operation(summary = "Consultar las suscripciones activas del usuario autenticado",
            description = "Devuelve una lista vacía si el usuario no tiene suscripciones activas.")
    public List<SubscriptionSummaryResponse> findActiveSubscriptions(
            @AuthenticationPrincipal AuthenticatedUser user) {
        return service.findActiveSubscriptions(user.id());
    }
}
