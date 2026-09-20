package com.gucardev.caching;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.time.Duration;

/**
 * One in-memory Caffeine {@link CacheManager} per TTL. Each manager creates caches on
 * demand (any cache name from {@link CacheNames}) and expires entries {@code afterWrite}
 * with its own fixed TTL. {@link CacheManagers#CAFFEINE_5M} is {@link Primary}, so it is
 * the fallback when {@code @Cacheable} omits {@code cacheManager}.
 */
@Configuration
public class CaffeineCacheConfig {

    @Bean(CacheManagers.CAFFEINE_30S)
    public CacheManager caffeine30s() {
        return manager(Duration.ofSeconds(30));
    }

    @Bean(CacheManagers.CAFFEINE_1M)
    public CacheManager caffeine1m() {
        return manager(Duration.ofMinutes(1));
    }

    @Bean(CacheManagers.CAFFEINE_3M)
    public CacheManager caffeine3m() {
        return manager(Duration.ofMinutes(3));
    }

    @Primary
    @Bean(CacheManagers.CAFFEINE_5M)
    public CacheManager caffeine5m() {
        return manager(Duration.ofMinutes(5));
    }

    @Bean(CacheManagers.CAFFEINE_10M)
    public CacheManager caffeine10m() {
        return manager(Duration.ofMinutes(10));
    }

    @Bean(CacheManagers.CAFFEINE_30M)
    public CacheManager caffeine30m() {
        return manager(Duration.ofMinutes(30));
    }

    @Bean(CacheManagers.CAFFEINE_1H)
    public CacheManager caffeine1h() {
        return manager(Duration.ofHours(1));
    }

    private CacheManager manager(Duration ttl) {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        cacheManager.setCaffeine(Caffeine.newBuilder().expireAfterWrite(ttl));
        return cacheManager;
    }
}
