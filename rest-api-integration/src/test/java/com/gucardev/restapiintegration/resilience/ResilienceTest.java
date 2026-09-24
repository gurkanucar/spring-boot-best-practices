package com.gucardev.restapiintegration.resilience;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gucardev.restapiintegration.IntegrationTestBase;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JCircuitBreakerFactory;

/** Retry, timeout and circuit breaker against real HTTP. The remote hit counter proves what was sent. */
class ResilienceTest extends IntegrationTestBase {

    @Autowired
    private UnreliableApiClient client;
    @Autowired
    private Resilience4JCircuitBreakerFactory circuitBreakerFactory;

    @BeforeEach
    void closeCircuit() {
        circuitBreakerFactory.getCircuitBreakerRegistry().find(CircuitBreakerConfiguration.REMOTE_API)
                .ifPresent(CircuitBreaker::reset);
    }

    // ---- retry ----

    @Test
    void withoutRetryTheFirst503IsTheAnswer() throws Exception {
        String key = uniqueKey();

        mockMvc.perform(get("/api/demo/retry/none").param("key", key).param("failTimes", "2"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.remoteStatus").value(503));
        assertThat(client.hits(key)).isEqualTo(1);
    }

    @Test
    void annotationRetriesUntilTheRemoteRecovers() throws Exception {
        String key = uniqueKey();

        mockMvc.perform(get("/api/demo/retry/annotation").param("key", key).param("failTimes", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attempt").value(3));
        assertThat(client.hits(key)).isEqualTo(3);
    }

    @Test
    void retryTemplateDoesTheSameProgrammatically() throws Exception {
        String key = uniqueKey();

        mockMvc.perform(get("/api/demo/retry/template").param("key", key).param("failTimes", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attempt").value(3));
        assertThat(client.hits(key)).isEqualTo(3);
    }

    @Test
    void retriesStopAfterMaxRetriesAndTheLastErrorIsReported() throws Exception {
        String key = uniqueKey();

        mockMvc.perform(get("/api/demo/retry/annotation").param("key", key).param("failTimes", "100"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.remoteStatus").value(503));
        assertThat(client.hits(key)).isEqualTo(4); // 1 call + 3 retries
    }

    @Test
    void clientErrorsAreNeverRetried() throws Exception {
        String key = uniqueKey();

        mockMvc.perform(get("/api/demo/retry/status/400").param("key", key))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.remoteStatus").value(400));
        assertThat(client.hits(key)).isEqualTo(1);
    }

    @Test
    void serverErrorsAreRetried() throws Exception {
        String key = uniqueKey();

        mockMvc.perform(get("/api/demo/retry/status/500").param("key", key)).andExpect(status().isBadGateway());
        assertThat(client.hits(key)).isEqualTo(4);
    }

    // ---- timeout ----

    @Test
    void slowRemoteHitsTheReadTimeoutAndBecomes504() throws Exception {
        long start = System.nanoTime();

        mockMvc.perform(get("/api/demo/timeout").param("delayMs", "3000"))
                .andExpect(status().isGatewayTimeout());

        // read timeout is 1s in the tests; we did not wait the full 3s
        assertThat((System.nanoTime() - start) / 1_000_000).isLessThan(2500);
    }

    @Test
    void fastEnoughRemoteIsFine() throws Exception {
        mockMvc.perform(get("/api/demo/timeout").param("delayMs", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.delayMs").value(50));
    }

    // ---- circuit breaker ----

    @Test
    void healthyRemoteAnswersThroughTheBreaker() throws Exception {
        mockMvc.perform(get("/api/demo/circuit-breaker").param("key", uniqueKey()).param("failTimes", "0"))
                .andExpect(jsonPath("$.source").value("remote"));
    }

    @Test
    void circuitOpensAfterRepeatedFailuresAndStopsCallingTheRemote() throws Exception {
        String key = uniqueKey();

        for (int i = 0; i < 4; i++) {
            mockMvc.perform(get("/api/demo/circuit-breaker").param("key", key))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.source").value("fallback"))
                    .andExpect(jsonPath("$.reason").value("remote call failed: RemoteApiServerException"));
        }
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(get("/api/demo/circuit-breaker").param("key", key))
                    .andExpect(jsonPath("$.reason").value("circuit open, remote not called"));
        }

        assertThat(client.hits(key)).isEqualTo(4);
        assertThat(circuitBreakerFactory.getCircuitBreakerRegistry().circuitBreaker(CircuitBreakerConfiguration.REMOTE_API).getState())
                .isEqualTo(CircuitBreaker.State.OPEN);
    }

    @Test
    void clientErrorsDoNotOpenTheCircuit() throws Exception {
        // 4xx is not recorded as a failure: the remote is healthy, our request was wrong.
        for (int i = 0; i < 6; i++) {
            circuitBreakerFactory.create(CircuitBreakerConfiguration.REMOTE_API)
                    .run(() -> client.statusWithRetry(404, uniqueKey()), t -> null);
        }

        assertThat(circuitBreakerFactory.getCircuitBreakerRegistry().circuitBreaker(CircuitBreakerConfiguration.REMOTE_API).getState())
                .isEqualTo(CircuitBreaker.State.CLOSED);
    }
}
