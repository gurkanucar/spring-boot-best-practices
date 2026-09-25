package com.gucardev.resillience4j.ratelimit;

/**
 * One counter a request is charged against, e.g. {@code client:acme-mobile}, {@code anon:3f2a...}
 * or {@code ip:203.0.113.7}. Requests with the same key share the limit.
 */
public record RateLimitBucket(String key, RateLimitProperties.Limit limit) {
}
