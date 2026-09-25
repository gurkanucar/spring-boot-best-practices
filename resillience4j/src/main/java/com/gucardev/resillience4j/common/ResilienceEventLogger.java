package com.gucardev.resillience4j.common;

import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Logs what Resilience4j decides, so the demo is visible in the console: each retry, each
 * circuit state change, each rejected call. Instances created later are picked up too.
 */
@Component
class ResilienceEventLogger {

    private static final Logger log = LoggerFactory.getLogger(ResilienceEventLogger.class);

    ResilienceEventLogger(RetryRegistry retries, CircuitBreakerRegistry circuitBreakers,
                          TimeLimiterRegistry timeLimiters, BulkheadRegistry bulkheads,
                          RateLimiterRegistry rateLimiters) {
        retries.getAllRetries().forEach(this::watch);
        retries.getEventPublisher().onEntryAdded(e -> watch(e.getAddedEntry()));
        circuitBreakers.getAllCircuitBreakers().forEach(this::watch);
        circuitBreakers.getEventPublisher().onEntryAdded(e -> watch(e.getAddedEntry()));
        timeLimiters.getAllTimeLimiters().forEach(this::watch);
        timeLimiters.getEventPublisher().onEntryAdded(e -> watch(e.getAddedEntry()));
        bulkheads.getAllBulkheads().forEach(this::watch);
        bulkheads.getEventPublisher().onEntryAdded(e -> watch(e.getAddedEntry()));
        rateLimiters.getAllRateLimiters().forEach(this::watch);
        rateLimiters.getEventPublisher().onEntryAdded(e -> watch(e.getAddedEntry()));
    }

    private void watch(Retry retry) {
        retry.getEventPublisher()
                .onRetry(e -> log.warn("[retry:{}] attempt {} failed ({}), next try in {} ms", e.getName(),
                        e.getNumberOfRetryAttempts(), shortName(e.getLastThrowable()), e.getWaitInterval().toMillis()))
                .onError(e -> log.error("[retry:{}] giving up after {} attempts ({})", e.getName(),
                        e.getNumberOfRetryAttempts(), shortName(e.getLastThrowable())))
                .onIgnoredError(e -> log.info("[retry:{}] not retried, not a transient error ({})", e.getName(),
                        shortName(e.getLastThrowable())));
    }

    private void watch(CircuitBreaker circuitBreaker) {
        circuitBreaker.getEventPublisher()
                .onStateTransition(e -> log.warn("[circuit-breaker:{}] {}", e.getCircuitBreakerName(),
                        e.getStateTransition()))
                .onCallNotPermitted(e -> log.info("[circuit-breaker:{}] call rejected, circuit is open",
                        e.getCircuitBreakerName()));
    }

    private void watch(TimeLimiter timeLimiter) {
        timeLimiter.getEventPublisher()
                .onTimeout(e -> log.warn("[time-limiter:{}] call timed out", e.getTimeLimiterName()));
    }

    private void watch(Bulkhead bulkhead) {
        bulkhead.getEventPublisher()
                .onCallRejected(e -> log.warn("[bulkhead:{}] call rejected, bulkhead is full", e.getBulkheadName()));
    }

    private void watch(RateLimiter rateLimiter) {
        rateLimiter.getEventPublisher()
                .onFailure(e -> log.warn("[rate-limiter:{}] no permit in time", e.getRateLimiterName()));
    }

    private static String shortName(Throwable throwable) {
        return throwable == null ? "-" : throwable.getClass().getSimpleName();
    }
}
