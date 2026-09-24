package com.gucardev.restapiintegration.resilience;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.stereotype.Component;

/** Wraps the remote call in a Resilience4j circuit breaker (through Spring Cloud CircuitBreaker). */
@Component
public class CircuitBreakerClient {

    private final CircuitBreakerFactory<?, ?> circuitBreakerFactory;
    private final UnreliableApiClient client;

    public CircuitBreakerClient(CircuitBreakerFactory<?, ?> circuitBreakerFactory, UnreliableApiClient client) {
        this.circuitBreakerFactory = circuitBreakerFactory;
        this.client = client;
    }

    public Map<String, Object> call(String key, int failTimes) {
        return circuitBreakerFactory.create(CircuitBreakerConfiguration.REMOTE_API).run(
                () -> withSource("remote", client.flaky(key, failTimes)),
                // The fallback runs for every failure, and instantly while the circuit is open.
                throwable -> withSource("fallback", Map.of("reason", reason(throwable))));
    }

    private static String reason(Throwable throwable) {
        return throwable instanceof CallNotPermittedException
                ? "circuit open, remote not called"
                : "remote call failed: " + throwable.getClass().getSimpleName();
    }

    private static Map<String, Object> withSource(String source, Map<String, Object> body) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("source", source);
        result.putAll(body);
        return result;
    }
}
