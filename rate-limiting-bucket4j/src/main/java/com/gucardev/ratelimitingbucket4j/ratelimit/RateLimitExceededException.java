package com.gucardev.ratelimitingbucket4j.ratelimit;

import com.gucardev.ratelimitingbucket4j.ratelimit.store.RateLimitStore;

/** Thrown by {@link RateLimiter}; {@link RateLimitExceptionHandler} turns it into a 429. */
public class RateLimitExceededException extends RuntimeException {

    private final RateLimitBucket bucket;
    private final long cost;
    private final RateLimitStore.Decision decision;

    public RateLimitExceededException(RateLimitBucket bucket, long cost, RateLimitStore.Decision decision) {
        super("Rate limit exceeded for " + bucket.key());
        this.bucket = bucket;
        this.cost = cost;
        this.decision = decision;
    }

    public RateLimitBucket bucket() {
        return bucket;
    }

    public long cost() {
        return cost;
    }

    public RateLimitStore.Decision decision() {
        return decision;
    }

    /** Whole seconds, rounded up and at least 1, as {@code Retry-After} requires. */
    public long retryAfterSeconds() {
        return Math.max(1, (decision.retryAfter().toMillis() + 999) / 1000);
    }
}
