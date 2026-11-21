package com.acme.salary.auth.web;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Describes the API for the Swagger page: sign in at POST /api/v1/auth/login, then Authorize with the token. */
@Configuration(proxyBeanMethods = false)
class OpenApiConfig {

    private static final String SCHEME = "bearerAuth";

    @Bean
    OpenAPI salaryOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("ACME Salary Management API")
                        .version("v1")
                        .description("Sign in with POST /api/v1/auth/login, copy the accessToken, then choose "
                                + "Authorize and paste it. Every other endpoint needs that bearer token."))
                .components(new Components().addSecuritySchemes(SCHEME, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(SCHEME));
    }
}
