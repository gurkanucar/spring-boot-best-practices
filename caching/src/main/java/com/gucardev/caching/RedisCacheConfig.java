package com.gucardev.caching;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import tools.jackson.databind.jsontype.PolymorphicTypeValidator;

@Configuration
public class RedisCacheConfig {

    @Value("${app.redis.key-prefix:caching:}")
    private String keyPrefix;

    // Type metadata preserves records and polymorphic fields; keep permitted types scoped.
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
        RedisCacheConfiguration defaults = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(ttl)
                .disableCachingNullValues()
                // Separate TTL namespaces, even when cache names and entry keys match.
                .prefixCacheNameWith(keyPrefix + "ttl:" + ttl.toSeconds() + "s:")
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(
                        GenericJacksonJsonRedisSerializer.builder()
                                .enableDefaultTyping(TYPE_VALIDATOR)
                                .build()));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaults)
                .build();
    }
}
