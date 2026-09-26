package com.gucardev.ratelimitingbucket4j.ratelimit.store;

import com.gucardev.ratelimitingbucket4j.ratelimit.RateLimitBucket;
import com.gucardev.ratelimitingbucket4j.ratelimit.RateLimitProperties;
import com.gucardev.ratelimitingbucket4j.ratelimit.store.RateLimitStore.Decision;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.ConsumptionProbe;
import java.time.Duration;

/** What both stores share: the bucket configuration and how a Bucket4j answer becomes a {@link Decision}. */
final class Buckets {

    private Buckets() {
    }

    /** For {@link RedisRateLimitStore}: the configuration Bucket4j stores with the bucket. */
    static BucketConfiguration configurationOf(RateLimitBucket bucket) {
        var builder = BucketConfiguration.builder();
        bucket.limits().forEach(limit -> builder.addLimit(bandwidthOf(limit)));
        return builder.build();
    }

    /** For {@link InMemoryRateLimitStore}: a bucket in this JVM. */
    static Bucket localBucketOf(RateLimitBucket bucket) {
        var builder = Bucket.builder();
        bucket.limits().forEach(limit -> builder.addLimit(bandwidthOf(limit)));
        return builder.build();
    }

    /**
     * {@code refillGreedy}: tokens come back evenly over the period (20 per minute = one every 3 s),
     * not all at once at the end of a window, so there is no window edge where a client could spend
     * two windows' worth at once.
     */
    private static Bandwidth bandwidthOf(RateLimitProperties.Limit limit) {
        return Bandwidth.builder()
                .capacity(limit.capacity())
                .refillGreedy(limit.capacity(), limit.period())
                .build();
    }

    static Decision decisionOf(ConsumptionProbe probe, RateLimitBucket bucket) {
        if (probe.isConsumed()) {
            return new Decision(true, probe.getRemainingTokens(), Duration.ZERO);
        }
        // Long.MAX_VALUE: the request costs more than a limit can ever hold. Retrying cannot help,
        // so answer with the longest period instead of "never".
        long nanos = probe.getNanosToWaitForRefill();
        Duration retryAfter = nanos == Long.MAX_VALUE ? bucket.longestPeriod() : Duration.ofNanos(nanos);
        return new Decision(false, probe.getRemainingTokens(), retryAfter);
    }
}
