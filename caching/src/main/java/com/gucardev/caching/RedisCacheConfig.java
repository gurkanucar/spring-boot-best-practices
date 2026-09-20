package com.gucardev.caching;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

/**
 * One distributed Redis {@link CacheManager} per TTL. Each manager creates caches on
 * demand (any cache name from {@link CacheNames}) with its own fixed {@code entryTtl}.
 *
 * <p>Keys are serialized as plain strings and values as JSON (Jackson 3 /
 * {@link GenericJacksonJsonRedisSerializer}). Every key is prefixed with the shared
 * {@code app.redis.key-prefix} (the same namespace used for all Redis keys, e.g. OTP) so
 * caches don't collide with other applications on a shared Redis — set it to e.g.
 * {@code "myapp:"} to get keys like {@code myapp:users::123}. Empty by default.
 *
 * <p>Managers build lazily, so the application starts even when Redis is unreachable;
 * connection happens on first use.
 */
@Configuration
public class RedisCacheConfig {

    /**
     * Shared Redis namespace (cache + OTP/etc.). Convention: include your own trailing
     * separator, e.g. {@code "myapp:"}. The cache name + "::" is appended automatically,
     * so {@code "myapp:"} yields {@code myapp:users::<key>}.
     */
    @Value("${app.redis.key-prefix:}")
    private String keyPrefix;

    @Bean(CacheManagers.REDIS_30S)
    public CacheManager redis30s(RedisConnectionFactory connectionFactory) {
        return manager(connectionFactory, Duration.ofSeconds(30));
    }

    @Bean(CacheManagers.REDIS_1M)
    public CacheManager redis1m(RedisConnectionFactory connectionFactory) {
        return manager(connectionFactory, Duration.ofMinutes(1));
    }

    @Bean(CacheManagers.REDIS_3M)
    public CacheManager redis3m(RedisConnectionFactory connectionFactory) {
        return manager(connectionFactory, Duration.ofMinutes(3));
    }

    @Bean(CacheManagers.REDIS_5M)
    public CacheManager redis5m(RedisConnectionFactory connectionFactory) {
        return manager(connectionFactory, Duration.ofMinutes(5));
    }

    @Bean(CacheManagers.REDIS_10M)
    public CacheManager redis10m(RedisConnectionFactory connectionFactory) {
        return manager(connectionFactory, Duration.ofMinutes(10));
    }

    @Bean(CacheManagers.REDIS_30M)
    public CacheManager redis30m(RedisConnectionFactory connectionFactory) {
        return manager(connectionFactory, Duration.ofMinutes(30));
    }

    @Bean(CacheManagers.REDIS_1H)
    public CacheManager redis1h(RedisConnectionFactory connectionFactory) {
        return manager(connectionFactory, Duration.ofHours(1));
    }

    private CacheManager manager(RedisConnectionFactory connectionFactory, Duration ttl) {
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(ttl)
                .disableCachingNullValues()
                .prefixCacheNameWith(keyPrefix)
                .serializeKeysWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(GenericJacksonJsonRedisSerializer.builder().build()));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(config)
                .build();
    }
}
