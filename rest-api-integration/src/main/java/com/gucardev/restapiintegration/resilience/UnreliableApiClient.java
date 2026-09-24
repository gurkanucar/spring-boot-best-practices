package com.gucardev.restapiintegration.resilience;

import com.gucardev.restapiintegration.client.error.RemoteApiServerException;
import java.time.Duration;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.retry.RetryListener;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryState;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.core.retry.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * Calls the deliberately unreliable remote endpoints, with and without retries.
 *
 * <p>Retry uses Spring Framework 7's built-in support: {@code @Retryable} on a bean method
 * (enabled with {@code @EnableResilientMethods}) or {@link RetryTemplate} in code. No extra
 * dependency is needed.
 */
@Component
public class UnreliableApiClient {

    private static final Logger log = LoggerFactory.getLogger(UnreliableApiClient.class);
    private static final ParameterizedTypeReference<Map<String, Object>> MAP = new ParameterizedTypeReference<>() {
    };

    private final RestClient restClient;
    private final RetryTemplate retryTemplate;

    public UnreliableApiClient(@Qualifier("remoteApi") RestClient restClient) {
        this.restClient = restClient;
        this.retryTemplate = new RetryTemplate(RetryPolicy.builder()
                .maxRetries(3)
                .delay(Duration.ofMillis(100))
                .multiplier(2)                      // 100 ms, 200 ms, 400 ms
                .maxDelay(Duration.ofSeconds(2))
                .includes(RemoteApiServerException.class, ResourceAccessException.class)
                .build());
        this.retryTemplate.setRetryListener(new RetryListener() {
            @Override
            public void beforeRetry(RetryPolicy policy, Retryable<?> retryable, RetryState state) {
                log.warn("Retrying remote call, retry #{}", state.getRetryCount());
            }
        });
    }

    /** One call, no retry. */
    public Map<String, Object> flaky(String key, int failTimes) {
        return restClient.get()
                .uri("/flaky?key={key}&failTimes={failTimes}", key, failTimes)
                .retrieve()
                .body(MAP);
    }

    /**
     * Declarative retry: up to 3 retries (4 attempts in total) with exponential backoff and jitter,
     * only for 5xx answers and I/O errors (connection refused, timeouts). A 4xx is never retried.
     * Must be called through the Spring proxy, i.e. from another bean.
     */
    @org.springframework.resilience.annotation.Retryable(
            includes = {RemoteApiServerException.class, ResourceAccessException.class},
            maxRetries = 3, delay = 100, multiplier = 2, maxDelay = 2000, jitter = 20)
    public Map<String, Object> flakyWithAnnotation(String key, int failTimes) {
        return flaky(key, failTimes);
    }

    /** Programmatic retry: the same policy as a {@link RetryTemplate}, plus a listener for logging. */
    public Map<String, Object> flakyWithTemplate(String key, int failTimes) {
        return retryTemplate.invoke(() -> flaky(key, failTimes));
    }

    /** Always answers with {@code status}; with the same retry policy as above. */
    @org.springframework.resilience.annotation.Retryable(
            includes = {RemoteApiServerException.class, ResourceAccessException.class},
            maxRetries = 3, delay = 100, multiplier = 2)
    public Map<String, Object> statusWithRetry(int status, String key) {
        return restClient.get().uri("/status/{status}?key={key}", status, key).retrieve().body(MAP);
    }

    /** No retry: a read timeout is usually NOT worth retrying blindly, the server may still be working. */
    public Map<String, Object> slow(long delayMs) {
        return restClient.get().uri("/slow?delayMs={delayMs}", delayMs).retrieve().body(MAP);
    }

    public int hits(String key) {
        Map<String, Object> body = restClient.get().uri("/hits?key={key}", key).retrieve().body(MAP);
        return ((Number) body.get("hits")).intValue();
    }
}
