package com.gucardev.resillience4j.circuitbreaker;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RecommendationController {

    private final RecommendationClient client;
    private final CircuitBreakerRegistry circuitBreakers;

    public RecommendationController(RecommendationClient client, CircuitBreakerRegistry circuitBreakers) {
        this.client = client;
        this.circuitBreakers = circuitBreakers;
    }

    @GetMapping("/api/products/{id}/recommendations")
    public RecommendationClient.Recommendations recommendations(@PathVariable long id) {
        return client.forProduct(id);
    }

    /** A short view of the circuit; the full one is at /actuator/circuitbreakers. */
    @GetMapping("/api/circuit-breakers/{name}")
    public Map<String, Object> state(@PathVariable String name) {
        CircuitBreaker circuitBreaker = circuitBreakers.circuitBreaker(name);
        CircuitBreaker.Metrics metrics = circuitBreaker.getMetrics();
        return Map.of(
                "state", circuitBreaker.getState(),
                "failureRate", metrics.getFailureRate(),
                "bufferedCalls", metrics.getNumberOfBufferedCalls(),
                "failedCalls", metrics.getNumberOfFailedCalls(),
                "notPermittedCalls", metrics.getNumberOfNotPermittedCalls());
    }
}
