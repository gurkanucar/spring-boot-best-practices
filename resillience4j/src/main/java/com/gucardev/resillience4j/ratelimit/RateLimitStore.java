package com.gucardev.resillience4j.ratelimit;

import java.time.Duration;

/** Where the counters live: in this JVM, or in Redis so that every app instance shares them. */
public interface RateLimitStore {

    /** Takes one permit from the bucket, if there is one left. */
    Decision tryConsume(RateLimitBucket bucket);

    /**
     * @param retryAfter when the next permit is available; zero when allowed
     */
    record Decision(boolean allowed, int limit, long remaining, Duration retryAfter) {
    }
}
