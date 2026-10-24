package com.juancruzy.earlybuyservice.shared.config;

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
                .info(new Info().title("Early Buy API").version("v1"))
                .components(new Components().addSecuritySchemes("bearerAuth", new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")));
    }

    @Bean
    public OpenApiCustomizer subscriptionSecurity() {
        return openApi -> {
            if (openApi.getPaths() == null) {
                return;
            }
            openApi.getPaths().forEach((path, item) -> {
                if (path.equals("/v1/subscriptions") || path.startsWith("/v1/subscriptions/")) {
                    item.readOperations().forEach(operation -> operation
                            .addSecurityItem(new SecurityRequirement().addList("bearerAuth")));
                }
            });
        };
    }
}
