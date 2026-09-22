package com.gucardev.caching;

/** Select the backing store and TTL at each cache annotation's call site. */
public final class CacheManagers {

    private CacheManagers() {
    }

    public static final String CAFFEINE_30S = "caffeineCacheManager30s";
    public static final String CAFFEINE_1M = "caffeineCacheManager1m";
    public static final String CAFFEINE_3M = "caffeineCacheManager3m";
    public static final String CAFFEINE_5M = "caffeineCacheManager5m";
    public static final String CAFFEINE_10M = "caffeineCacheManager10m";
    public static final String CAFFEINE_30M = "caffeineCacheManager30m";
    public static final String CAFFEINE_1H = "caffeineCacheManager1h";

    public static final String REDIS_30S = "redisCacheManager30s";
    public static final String REDIS_1M = "redisCacheManager1m";
    public static final String REDIS_3M = "redisCacheManager3m";
    public static final String REDIS_5M = "redisCacheManager5m";
    public static final String REDIS_10M = "redisCacheManager10m";
    public static final String REDIS_30M = "redisCacheManager30m";
    public static final String REDIS_1H = "redisCacheManager1h";
}
