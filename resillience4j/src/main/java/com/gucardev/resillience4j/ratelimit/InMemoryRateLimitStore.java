package com.gucardev.resillience4j.ratelimit;

import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.internal.AtomicRateLimiter;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * One Resilience4j {@link RateLimiter} per bucket key, created on first use.
 *
 * <p>A Resilience4j rate limiter is one counter. {@code @RateLimiter(name = "x")} therefore limits
 * ALL callers together, which is right for an outbound call (see SmsClient) but wrong for
 * protecting an API from its clients. Per-client limiting needs one limiter per client key.
 *
 * <p>Counters live in this JVM only: with 3 instances behind a load balancer a client effectively
 * gets 3x the limit. Use {@link RedisRateLimitStore} ({@code app.rate-limit.store=redis}) then.
 */
public class InMemoryRateLimitStore implements RateLimitStore {

    private final Map<String, Entry> limiters = new ConcurrentHashMap<>();

    @Override
    public Decision tryConsume(RateLimitBucket bucket) {
        Entry entry = limiters.computeIfAbsent(bucket.key(), key -> new Entry(new AtomicRateLimiter(key, RateLimiterConfig.custom()
                .limitForPeriod(bucket.limit().limit())
                .limitRefreshPeriod(bucket.limit().period())
                .timeoutDuration(Duration.ZERO) // an API request is rejected at once, not parked
                .build()), bucket.limit().period()));
        entry.touch();
        boolean allowed = entry.limiter().acquirePermission();
        // AtomicRateLimiter is the default implementation behind RateLimiter.of(...); it also knows the wait time.
        AtomicRateLimiter.AtomicRateLimiterMetrics metrics = entry.limiter().getDetailedMetrics();
        Duration retryAfter = allowed ? Duration.ZERO : Duration.ofNanos(Math.max(0, metrics.getNanosToWait()));
        return new Decision(allowed, bucket.limit().limit(), Math.max(0, metrics.getAvailablePermissions()), retryAfter);
    }

    /**
     * Forgets clients that were idle for a whole period. Their limiter would be full again anyway,
     * so dropping it changes nothing, and memory does not grow with every IP that ever called.
     */
    @Scheduled(fixedDelay = 60_000)
    void evictIdle() {
        long now = System.nanoTime();
        limiters.values().removeIf(entry -> now - entry.lastUsedNanos > entry.period().toNanos());
    }

    int size() {
        return limiters.size();
    }

    private static final class Entry {
        private final AtomicRateLimiter limiter;
        private final Duration period;
        private volatile long lastUsedNanos = System.nanoTime();

        Entry(AtomicRateLimiter limiter, Duration period) {
            this.limiter = limiter;
            this.period = period;
        }

        AtomicRateLimiter limiter() {
            return limiter;
        }

        Duration period() {
            return period;
        }

        void touch() {
            lastUsedNanos = System.nanoTime();
        }
    }
}
