package com.gucardev.restapiintegration.resilience;

import com.gucardev.restapiintegration.client.error.RemoteApiServerException;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import java.time.Duration;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JCircuitBreakerFactory;
import org.springframework.cloud.client.circuitbreaker.Customizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.ResourceAccessException;

@Configuration
class CircuitBreakerConfiguration {

    static final String REMOTE_API = "remote-api";

    /**
     * Opens the circuit when at least 50% of the last 4 calls failed. While open, calls fail
     * immediately (no request is sent) for 30 seconds; then one trial call decides whether to
     * close it again. Only 5xx and I/O errors count as failures: a 4xx means our request was
     * wrong, not that the remote side is unhealthy.
     */
    @Bean
    Customizer<Resilience4JCircuitBreakerFactory> remoteApiCircuitBreaker() {
        return factory -> factory.configure(builder -> builder.circuitBreakerConfig(CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(4)
                .minimumNumberOfCalls(4)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .permittedNumberOfCallsInHalfOpenState(1)
                .recordExceptions(RemoteApiServerException.class, ResourceAccessException.class)
                .build()), REMOTE_API);
    }
}
