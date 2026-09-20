package com.gucardev.caching;

import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.interceptor.LoggingCacheErrorHandler;
import org.springframework.context.annotation.Configuration;

/**
 * Enables Spring's annotation-driven cache management. The actual cache managers are
 * declared in {@link CaffeineCacheConfig} and {@link RedisCacheConfig}.
 *
 * <p>Implementing {@link CachingConfigurer} to supply {@link #errorHandler()} is what
 * makes caching fail-safe: without it, Spring's default {@code SimpleCacheErrorHandler}
 * rethrows any exception a cache operation throws, so a {@code @Cacheable} call fails
 * outright the moment Redis is unreachable or a stored entry no longer deserializes
 * (e.g. after a DTO shape change). {@link LoggingCacheErrorHandler} logs the failure
 * instead and lets the caching aspect fall through — a failed GET is treated as a cache
 * miss (the real method still runs), and a failed PUT/EVICT/CLEAR is simply dropped.
 * The {@code false} argument keeps the log line to a message, no stack trace, so a
 * Redis outage doesn't flood the logs with a trace on every single call. Flip it to
 * {@code true} temporarily when diagnosing a caching problem — that's exactly how the
 * {@code java.math.BigDecimal} validator gap in {@link RedisCacheConfig} was found:
 * the one-line message alone wasn't enough to tell {@code ClassCastException} apart
 * from a {@code SerializationException} buried three frames deeper.
 *
 * <p>This alone is not enough for a snappy fail-safe: without a short Redis command
 * timeout, a blocked call can hang for Lettuce's 60s default before this handler ever
 * gets to swallow anything. See {@code application.yaml}'s {@code spring.data.redis.timeout}
 * / {@code connect-timeout}.
 */
@Configuration
@EnableCaching
public class CachingConfig implements CachingConfigurer {

    @Override
    public CacheErrorHandler errorHandler() {
        return new LoggingCacheErrorHandler(false);
    }
}
