package com.juancruzy.earlybuyservice.shared.config;

import java.util.Map;

import com.juancruzy.earlybuyservice.shared.exception.ErrorCode;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

    @Bean
    public OpenAPI earlyBuyOpenApi() {
        return new OpenAPI()
                .info(new Info().title("Early Buy API").version("v1")
                        .description("""
                                Suscripciones a artículos sin stock. Requiere permiso user de Auth.
                                Obtener el campo token con POST http://localhost:3000/users/signin,
                                enviando login y password. En Authorize ingresar solo el token, sin el prefijo Bearer.
                                Early Buy consulta GET /users/current de Auth en cada solicitud.
                                Los IDs son cadenas hexadecimales ObjectId y las fechas se expresan en UTC.
                                """))
                .components(new Components().addSecuritySchemes("bearerAuth", new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")
                        .description("Token emitido por Auth con permiso user. Ingresar el token sin Bearer.")));
    }

    @Bean
    public OpenApiCustomizer subscriptionSecurity() {
        return openApi -> {
            if (openApi.getPaths() == null) {
                return;
            }
            openApi.getPaths().forEach((path, item) -> {
                if (path.equals("/v1/subscriptions") || path.startsWith("/v1/subscriptions/")) {
                    item.readOperations().forEach(operation -> {
                        operation.addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
                        if (operation.getResponses() == null) {
                            return;
                        }
                        operation.getResponses().forEach((status, response) -> {
                            if (response.getContent() == null || response.getDescription() == null) {
                                return;
                            }
                            var json = response.getContent().get("application/json");
                            if (json == null) {
                                return;
                            }
                            for (ErrorCode code : ErrorCode.values()) {
                                if (status.equals(Integer.toString(code.getStatus().value()))
                                        && response.getDescription().contains(code.name())) {
                                    json.addExamples(code.name(), new Example().value(Map.of(
                                            "timestamp", "2026-11-20T12:00:00Z", "status", code.getStatus().value(),
                                            "code", code.name(), "message", code.getDefaultMessage(), "path", path)));
                                }
                            }
                        });
                    });
                }
            });
        };
    }
}
