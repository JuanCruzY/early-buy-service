package com.juancruzy.earlybuyservice.subscription.controller;

import java.util.List;

import com.juancruzy.earlybuyservice.auth.security.AuthenticatedUser;
import com.juancruzy.earlybuyservice.subscription.dto.CreateSubscriptionRequest;
import com.juancruzy.earlybuyservice.subscription.dto.SubscriptionResponse;
import com.juancruzy.earlybuyservice.subscription.dto.SubscriptionSummaryResponse;
import com.juancruzy.earlybuyservice.subscription.service.SubscriptionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import com.juancruzy.earlybuyservice.shared.exception.ApiError;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/subscriptions")
@ApiResponses({
        @ApiResponse(responseCode = "401", description = "MISSING_JWT o INVALID_JWT",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "500", description = "INTERNAL_ERROR",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class)))
})
public class SubscriptionController {
    private final SubscriptionService service;

    public SubscriptionController(SubscriptionService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Suscribirse a un artículo sin stock",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true))
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Suscripción creada",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = SubscriptionResponse.class),
                            examples = @ExampleObject(value = """
                                    {"subscriptionId":"507f1f77bcf86cd799439012","articleId":"507f1f77bcf86cd799439011",
                                     "userId":"507f1f77bcf86cd799439013","contact":"usuario@correo.com","channel":"EMAIL",
                                     "status":"SUBSCRIBED","createdAt":"2026-11-20T12:00:00Z","cancelledAt":null,"notifiedAt":null}
                                    """))),
            @ApiResponse(responseCode = "400", description = "MISSING_FIELDS, INVALID_CHANNEL, INVALID_ARTICLE_ID, INVALID_CONTACT o INVALID_REQUEST",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "403", description = "ACCESS_DENIED",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "ARTICLE_NOT_FOUND. Incluye artículos deshabilitados",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "409", description = "ALREADY_SUBSCRIBED o ARTICLE_HAS_STOCK",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "503", description = "AUTH_UNAVAILABLE, TIMEOUT_CATALOG o CATALOG_UNAVAILABLE",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class)))
    })
    public SubscriptionResponse subscribe(@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user,
                                          @RequestBody(required = false) CreateSubscriptionRequest request) {
        return service.subscribe(user.id(), request);
    }

    @DeleteMapping("/{subscriptionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Cancelar una suscripción activa del usuario autenticado")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Suscripción cancelada", content = @Content),
            @ApiResponse(responseCode = "403", description = "ACCESS_DENIED o NOT_OWNER",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "SUBSCRIPTION_NOT_FOUND. Incluye IDs con formato inválido",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "409", description = "INVALID_STATUS_TRANSITION",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "503", description = "AUTH_UNAVAILABLE",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class)))
    })
    public void cancel(@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user,
                       @Parameter(example = "507f1f77bcf86cd799439012") @PathVariable String subscriptionId) {
        service.cancel(user.id(), subscriptionId);
    }

    @GetMapping
    @Operation(summary = "Consultar las suscripciones activas del usuario autenticado",
            description = "Devuelve una lista vacía si el usuario no tiene suscripciones activas.")
    @ApiResponses({
            @ApiResponse(responseCode = "403", description = "ACCESS_DENIED",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "503", description = "AUTH_UNAVAILABLE",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class)))
    })
    @ApiResponse(responseCode = "200", description = "Suscripciones activas del usuario",
            content = @Content(mediaType = "application/json",
                    array = @ArraySchema(schema = @Schema(implementation = SubscriptionSummaryResponse.class)),
                    examples = {
                            @ExampleObject(name = "Sin suscripciones", value = "[]"),
                            @ExampleObject(name = "Suscripción activa", value = """
                                    [{"subscriptionId":"507f1f77bcf86cd799439012","articleId":"507f1f77bcf86cd799439011",
                                      "channel":"EMAIL","contact":"usuario@correo.com"}]
                                    """)
                    }))
    public List<SubscriptionSummaryResponse> findActiveSubscriptions(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user) {
        return service.findActiveSubscriptions(user.id());
    }
}
