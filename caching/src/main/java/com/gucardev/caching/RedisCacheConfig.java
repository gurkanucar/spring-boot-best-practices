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
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import tools.jackson.databind.jsontype.PolymorphicTypeValidator;

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
 * <p><b>Default typing is deliberately enabled</b> (with a {@link PolymorphicTypeValidator}).
 * Without it, {@code GenericJacksonJsonRedisSerializer} writes plain JSON with no
 * {@code @class} hint, so reading a cached value back deserializes into a generic
 * {@code LinkedHashMap} instead of your actual type — the cache write "succeeds" silently,
 * and the NEXT read (the cache hit) throws {@code ClassCastException} when Spring's proxy
 * tries to return it as the method's real return type. This is easy to miss because a single
 * call always looks fine; it's the second call — the hit — that breaks.
 *
 * <p>The validator allows {@code com.gucardev.caching.*} (this project's own types) plus
 * {@code java.math.*}/{@code java.time.*}/{@code java.util.*} — not because those packages
 * are "trusted" in some deep sense, but because Jackson's default typing tags certain
 * JDK scalar types (confirmed: {@code BigDecimal}) even under {@code NON_FINAL} mode, since
 * a bare JSON number is ambiguous — it could be a {@code Double} or a {@code BigDecimal} —
 * and a validator that only allowed this project's own package rejected resolving that tag,
 * breaking every cached value containing one. This is deliberately NOT
 * {@code enableUnsafeDefaultTyping()}, which allows deserializing ANY class on the classpath:
 * Redis is an external, potentially-shared store, and an unrestricted validator turns
 * "whatever JSON happens to be at this key" into "whatever class Jackson is told to
 * instantiate" — a real deserialization attack surface, not just a style preference.
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

    private static final PolymorphicTypeValidator TYPE_VALIDATOR = BasicPolymorphicTypeValidator.builder()
            .allowIfSubType("com.gucardev.caching.")
            .allowIfSubType("java.math.")
            .allowIfSubType("java.time.")
            .allowIfSubType("java.util.")
            .build();

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
                        .fromSerializer(GenericJacksonJsonRedisSerializer.builder()
                                .enableDefaultTyping(TYPE_VALIDATOR)
                                .build()));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(config)
                .build();
    }
}
