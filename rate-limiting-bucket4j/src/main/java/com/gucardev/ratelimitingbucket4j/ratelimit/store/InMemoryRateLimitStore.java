package com.gucardev.ratelimitingbucket4j.ratelimit.store;

import com.gucardev.ratelimitingbucket4j.ratelimit.RateLimitBucket;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bucket;
import java.time.Duration;

/**
 * One Bucket4j bucket per key, in this JVM.
 *
 * <p>Counters live in this instance only: with 3 instances behind a load balancer a client
 * effectively gets 3x the limit. Use {@link RedisRateLimitStore} ({@code app.rate-limit.store=redis}) then.
 */
public class InMemoryRateLimitStore implements RateLimitStore {

    private final Cache<String, Bucket> buckets;

    /**
     * @param idleTimeout forget a client after this long without requests; it must be at least the
     *                    longest period, when its bucket would be full again anyway. Memory then does
     *                    not grow with every IP that ever called.
     */
    public InMemoryRateLimitStore(Duration idleTimeout) {
        this.buckets = Caffeine.newBuilder().expireAfterAccess(idleTimeout).build();
    }

    @Override
    public Decision tryConsume(RateLimitBucket bucket, long tokens) {
        Bucket tokenBucket = buckets.get(bucket.key(), key -> Buckets.localBucketOf(bucket));
        return Buckets.decisionOf(tokenBucket.tryConsumeAndReturnRemaining(tokens), bucket);
    }
}
