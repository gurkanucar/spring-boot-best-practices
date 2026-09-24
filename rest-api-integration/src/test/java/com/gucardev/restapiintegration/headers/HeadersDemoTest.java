package com.gucardev.restapiintegration.headers;

import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gucardev.restapiintegration.IntegrationTestBase;
import org.junit.jupiter.api.Test;

/** What the remote side really received, as reported by its echo endpoint. */
class HeadersDemoTest extends IntegrationTestBase {

    @Test
    void defaultInterceptorAndPerRequestHeadersAllArrive() throws Exception {
        mockMvc.perform(get("/api/demo/headers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.method").value("GET"))
                .andExpect(jsonPath("$.query").value("page=1"))
                // client defaults
                .andExpect(jsonPath("$.headers.authorization").value("Basic ***"))
                .andExpect(jsonPath("$.headers.x-api-key").value("demo-api-key"))
                .andExpect(jsonPath("$.headers.user-agent").value("rest-api-integration/1.0"))
                .andExpect(jsonPath("$.headers.accept").value("application/json"))
                // interceptor
                .andExpect(jsonPath("$.headers.x-correlation-id").value(matchesPattern("[0-9a-f-]{36}")))
                // this request only
                .andExpect(jsonPath("$.headers.x-request-source").value("headers-demo"))
                .andExpect(jsonPath("$.headers.accept-language").value("tr-TR"));
    }

    @Test
    void incomingCorrelationIdIsPropagated() throws Exception {
        mockMvc.perform(get("/api/demo/headers").header("X-Correlation-Id", "abc-123"))
                .andExpect(jsonPath("$.headers.x-correlation-id").value("abc-123"));
    }

    @Test
    void perRequestAuthorizationReplacesTheDefault() throws Exception {
        mockMvc.perform(get("/api/demo/headers").param("bearer", "true"))
                .andExpect(jsonPath("$.headers.authorization").value("Bearer ***"));
    }
}
