package com.gucardev.resillience4j.circuitbreaker;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * CIRCUIT BREAKER: the recommendation engine is down for minutes, not milliseconds. Retrying
 * would only add load to a service that is already struggling, and make every product page wait.
 *
 * <p>After enough failures the circuit OPENS: calls fail instantly without touching the remote,
 * and the page shows bestsellers instead. After {@code wait-duration-in-open-state} it goes
 * HALF_OPEN, lets a few trial calls through, and closes again if they succeed.
 *
 * <p>Recommendations are optional content, so a fallback is fine. Do not invent a fallback for
 * something that must be correct (a payment, a stock level).
 */
@Component
public class RecommendationClient {

    private static final Logger log = LoggerFactory.getLogger(RecommendationClient.class);
    private static final List<Long> BESTSELLERS = List.of(100L, 200L, 300L);

    private final RestClient fakeApi;

    public RecommendationClient(@Lazy RestClient fakeApi) {
        this.fakeApi = fakeApi;
    }

    @CircuitBreaker(name = "recommendations", fallbackMethod = "bestsellers")
    public Recommendations forProduct(long productId) {
        log.info("Calling recommendation engine for product {}", productId);
        RemoteRecommendations remote = fakeApi.get().uri("/recommendations/{id}", productId)
                .retrieve().body(RemoteRecommendations.class);
        return new Recommendations(productId, remote.productIds(), "recommendation-engine", null);
    }

    /** Same parameters plus the exception. Runs on every failure, and immediately while the circuit is open. */
    private Recommendations bestsellers(long productId, Throwable cause) {
        String reason = cause instanceof CallNotPermittedException
                ? "circuit open, recommendation engine not called"
                : "recommendation engine failed: " + cause.getClass().getSimpleName();
        return new Recommendations(productId, BESTSELLERS, "fallback-bestsellers", reason);
    }

    record RemoteRecommendations(List<Long> productIds) {
    }

    public record Recommendations(long productId, List<Long> productIds, String source, String fallbackReason) {
    }
}
