package com.gucardev.swaggeropenapidocs;

import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.client.RestTestClient;

// application-prod.yaml turns both off - this is what actually proves it, not just the yaml.
@SpringBootTest(webEnvironment = RANDOM_PORT)
@AutoConfigureRestTestClient
@ActiveProfiles("prod")
class SwaggerDisabledInProdTest {

    @Autowired
    private RestTestClient client;

    @Test
    void apiDocsIsNotServed() {
        client.get().uri("/v3/api-docs").exchange().expectStatus().isNotFound();
    }

    @Test
    void swaggerUiIsNotServed() {
        client.get().uri("/swagger-ui.html").exchange().expectStatus().isNotFound();
    }
}
