package com.gucardev.ratelimitingbucket4j.ratelimit;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * One token bucket a request is charged against, e.g. {@code client:acme-mobile}, {@code anon:3f2a...}
 * or {@code ip:203.0.113.7}. Requests with the same key share its tokens; every limit must have enough.
 */
public record RateLimitBucket(String key, List<RateLimitProperties.Limit> limits) {

    /** The limit with the fewest tokens, reported as {@code RateLimit-Limit}. */
    public int smallestCapacity() {
        return limits.stream().mapToInt(RateLimitProperties.Limit::capacity).min().orElse(0);
    }

    public Duration longestPeriod() {
        return limits.stream().map(RateLimitProperties.Limit::period).max(Comparator.naturalOrder()).orElse(Duration.ZERO);
    }

    /** All limits in the IETF draft format, e.g. {@code 20;w=60, 5;w=1}. */
    public String policy() {
        return limits.stream()
                .map(limit -> limit.capacity() + ";w=" + limit.period().toSeconds())
                .collect(Collectors.joining(", "));
    }
}
