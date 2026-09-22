package com.gucardev.caching;

import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

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
        CaffeineCacheManager manager = new CaffeineCacheManager();
        manager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(1_000)
                .expireAfterWrite(ttl));
        return manager;
    }
}
