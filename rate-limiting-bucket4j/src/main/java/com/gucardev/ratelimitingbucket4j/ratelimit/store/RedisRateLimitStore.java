package com.gucardev.ratelimitingbucket4j.ratelimit.store;

import com.gucardev.ratelimitingbucket4j.ratelimit.RateLimitBucket;
import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.lettuce.Bucket4jLettuce;
import io.lettuce.core.api.StatefulRedisConnection;
import java.time.Duration;

/**
 * Token buckets in Redis, shared by every instance of the app.
 *
 * <p>Bucket4j keeps each bucket's state in one Redis key and updates it with compare-and-swap:
 * read the state, compute the new one, write it only if nobody changed it in between, otherwise
 * retry. No lost updates, even with many instances consuming from the same bucket at once.
 */
public class RedisRateLimitStore implements RateLimitStore {

    private static final String KEY_PREFIX = "rate-limit:";

    private final ProxyManager<String> buckets;

    public RedisRateLimitStore(StatefulRedisConnection<String, byte[]> connection) {
        this.buckets = Bucket4jLettuce.casBasedBuilder(connection)
                // A key expires once its bucket would be full again: an idle client costs no memory.
                .expirationAfterWrite(ExpirationAfterWriteStrategy.basedOnTimeForRefillingBucketUpToMax(Duration.ofSeconds(10)))
                .build();
    }

    @Override
    public Decision tryConsume(RateLimitBucket bucket, long tokens) {
        var tokenBucket = buckets.builder().build(KEY_PREFIX + bucket.key(), () -> Buckets.configurationOf(bucket));
        return Buckets.decisionOf(tokenBucket.tryConsumeAndReturnRemaining(tokens), bucket);
    }
}
