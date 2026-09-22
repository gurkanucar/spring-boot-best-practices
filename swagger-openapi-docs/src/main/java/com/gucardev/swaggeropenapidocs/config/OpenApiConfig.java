package com.gucardev.swaggeropenapidocs.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.ExternalDocumentation;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI openApi(
            @Value("${spring.application.name}") String applicationName,
            @Value("${app.self-base-url}") String selfBaseUrl) {

        return new OpenAPI()
                .info(new Info()
                        .title(applicationName + " API")
                        .version("0.0.1-SNAPSHOT")
                        .description("""
                                Reference catalog of springdoc-openapi / Swagger annotations: per-field \
                                @Schema examples, named request-body @ExampleObject variants, documented \
                                response codes, and a security scheme that is documented but not enforced.

                                ### Authentication
                                No endpoint here actually requires it, but a protected API would expect:

                                `Authorization: Bearer <token>`
                                """)
                        .contact(new Contact()
                                .name("Gucar Development")
                                .url("https://github.com/gurkanucar"))
                        .license(new License()
                                .name("MIT")
                                .url("https://opensource.org/licenses/MIT")))
                .servers(List.of(new Server().url(selfBaseUrl).description("Local")))
                .components(new Components()
                        .addSecuritySchemes(
                                "bearerAuth",
                                new SecurityScheme()
                                        .name("bearerAuth")
                                        .type(SecurityScheme.Type.HTTP)
                                        .in(SecurityScheme.In.HEADER)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("JWT access token (documented for reference only)")))
                .tags(List.of(new Tag()
                        .name("Products")
                        .description("Product catalog: CRUD-style endpoints written to exercise every "
                                + "common OpenAPI documentation technique")))
                .externalDocs(new ExternalDocumentation()
                        .description("Project documentation")
                        .url("https://github.com/gucardev"));
    }
}
