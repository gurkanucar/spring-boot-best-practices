package com.gucardev.restapidesign.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI restApiDesignOpenApi() {
        return new OpenAPI().info(new Info()
                .title("REST API Design Best Practices")
                .description("Example API demonstrating CRUD, PATCH, business actions, relationships, "
                        + "pagination, versioning, and optimistic concurrency.")
                .version("v1"));
    }
}
