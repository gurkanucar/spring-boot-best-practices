package com.gucardev.caching;

import static org.assertj.core.api.Assertions.assertThat;

import com.gucardev.caching.examples.dto.FakeOrder;
import java.time.Duration;
import java.util.Objects;
import java.util.HashSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.context.ApplicationContext;
import org.springframework.data.redis.cache.RedisCache;

@SpringBootTest
class CacheConfigurationTest {

    @Autowired ApplicationContext context;
    @Autowired CacheManager defaultManager;
    @Autowired @Qualifier(CacheManagers.CAFFEINE_5M) CacheManager caffeine;
    @Autowired @Qualifier(CacheManagers.REDIS_30S) CacheManager redis;

    @Test
    void allTtlManagersAreAvailableAndFiveMinuteCaffeineIsTheDefault() {
        assertThat(context.getBeansOfType(CacheManager.class)).hasSize(14);
        assertThat(defaultManager).isSameAs(caffeine);
    }

    @ParameterizedTest
    @CsvSource({"30s,30", "1m,60", "3m,180", "5m,300", "10m,600", "30m,1800", "1h,3600"})
    void eachManagerAppliesItsSelectedTtlToNewCacheNames(String suffix, long seconds) {
        var localManager = context.getBean("caffeineCacheManager" + suffix, CacheManager.class);
        var redisManager = context.getBean("redisCacheManager" + suffix, CacheManager.class);
        var local = (CaffeineCache) Objects.requireNonNull(localManager.getCache("new-cache"));
        var remote = (RedisCache) Objects.requireNonNull(redisManager.getCache("new-cache"));
        assertThat(local.getNativeCache().policy().expireAfterWrite().orElseThrow().getExpiresAfter())
                .isEqualTo(Duration.ofSeconds(seconds));
        assertThat(remote.getCacheConfiguration().getTtlFunction().getTimeToLive("key", "value"))
                .isEqualTo(Duration.ofSeconds(seconds));
    }

    @Test
    void redisTtlManagersUseDistinctKeyNamespaces() {
        var prefixes = new HashSet<String>();
        for (String name : new String[]{CacheManagers.REDIS_30S, CacheManagers.REDIS_1M,
                CacheManagers.REDIS_3M, CacheManagers.REDIS_5M, CacheManagers.REDIS_10M,
                CacheManagers.REDIS_30M, CacheManagers.REDIS_1H}) {
            var manager = context.getBean(name, CacheManager.class);
            var cache = (RedisCache) Objects.requireNonNull(manager.getCache(CacheNames.USERS));
            prefixes.add(cache.getCacheConfiguration().getKeyPrefixFor(CacheNames.USERS));
        }
        assertThat(prefixes).hasSize(7)
                .contains("caching:ttl:60s:users::", "caching:ttl:300s:users::");
    }

    @Test
    void localTtlManagersKeepTheSameKeyIndependentIncludingEviction() {
        var oneMinute = Objects.requireNonNull(context.getBean(CacheManagers.CAFFEINE_1M, CacheManager.class)
                .getCache("ttl-isolation"));
        var fiveMinutes = Objects.requireNonNull(caffeine.getCache("ttl-isolation"));
        try {
            oneMinute.put("same-key", "short-lived");
            assertThat(fiveMinutes.get("same-key")).isNull();
            fiveMinutes.put("same-key", "long-lived");
            assertThat(oneMinute.get("same-key", String.class)).isEqualTo("short-lived");
            oneMinute.evict("same-key");
            assertThat(fiveMinutes.get("same-key", String.class)).isEqualTo("long-lived");
        } finally {
            oneMinute.clear();
            fiveMinutes.clear();
        }
    }

    @Test
    void localCacheEnforcesItsSizeBound() {
        var cache = ((CaffeineCache) Objects.requireNonNull(caffeine.getCache(CacheNames.EXAMPLE_PRODUCTS))).getNativeCache();
        try {
            for (int i = 0; i < 1_100; i++) {
                cache.put("capacity-test-" + i, i);
            }
            cache.cleanUp();
            assertThat(cache.estimatedSize()).isLessThanOrEqualTo(1_000);
        } finally {
            cache.invalidateAll();
        }
    }

    @Test
    void configuredRedisSerializerPreservesNestedAndPolymorphicValuesWithoutAServer() {
        var cache = (RedisCache) Objects.requireNonNull(redis.getCache(CacheNames.EXAMPLE_ORDERS));
        var serialization = cache.getCacheConfiguration().getValueSerializationPair();
        for (FakeOrder order : new FakeOrder[]{FakeOrder.fakeStandard(), FakeOrder.fakeExpress()}) {
            assertThat(serialization.read(serialization.write(order))).isEqualTo(order);
        }
    }
}
