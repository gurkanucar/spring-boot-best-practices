package com.gucardev.resillience4j.ratelimit;

import java.time.Duration;
import java.util.List;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

/**
 * Fixed-window counters in Redis, shared by every instance of the app.
 *
 * <p>The Lua script runs atomically inside Redis: increment the bucket, start its expiry on the
 * first request of the window, and return the count and the time left. No race between
 * "read the counter" and "write the counter", even with many instances.
 *
 * <p>Fixed windows allow a burst of up to 2x the limit around a window edge (end of one window,
 * start of the next). If that matters, use a sliding window or a token bucket (e.g. Bucket4j with
 * its Redis integration); the {@link RateLimitStore} interface stays the same.
 */
public class RedisRateLimitStore implements RateLimitStore {

    private static final String KEY_PREFIX = "rate-limit:";

    @SuppressWarnings("rawtypes")
    private static final RedisScript<List> FIXED_WINDOW = RedisScript.of("""
            local count = redis.call('INCR', KEYS[1])
            if count == 1 then
              redis.call('PEXPIRE', KEYS[1], ARGV[1])
            end
            return {count, redis.call('PTTL', KEYS[1])}
            """, List.class);

    private final StringRedisTemplate redis;

    public RedisRateLimitStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public Decision tryConsume(RateLimitBucket bucket) {
        int limit = bucket.limit().limit();
        List<?> result = redis.execute(FIXED_WINDOW, List.of(KEY_PREFIX + bucket.key()),
                String.valueOf(bucket.limit().period().toMillis()));
        long count = ((Number) result.get(0)).longValue();
        long ttlMs = Math.max(0, ((Number) result.get(1)).longValue());
        boolean allowed = count <= limit;
        return new Decision(allowed, limit, Math.max(0, limit - count), allowed ? Duration.ZERO : Duration.ofMillis(ttlMs));
    }
}
