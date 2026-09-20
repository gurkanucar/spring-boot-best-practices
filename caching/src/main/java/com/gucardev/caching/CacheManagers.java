package com.gucardev.caching;

/**
 * Bean names of the available cache managers. The manager you pick determines BOTH the
 * backing store (Caffeine in-memory vs. Redis) AND the TTL — there is one manager per
 * (store, TTL) pair.
 *
 * <p>Cache names ({@link CacheNames}) carry no TTL; they are just logical buckets. Select
 * the TTL by choosing the matching manager:
 *
 * <pre>{@code
 * // "users" cache, in Redis, entries live 10 minutes
 * @Cacheable(cacheNames = CacheNames.USERS, cacheManager = CacheManagers.REDIS_10M)
 *
 * // "users" cache, in-memory Caffeine, entries live 30 seconds
 * @Cacheable(cacheNames = CacheNames.USERS, cacheManager = CacheManagers.CAFFEINE_30S)
 * }</pre>
 */
public final class CacheManagers {

    private CacheManagers() {
    }

    /* ---- In-memory Caffeine managers (CAFFEINE_5M is the @Primary default) ---- */

    public static final String CAFFEINE_30S = "caffeineCacheManager30s";
    public static final String CAFFEINE_1M = "caffeineCacheManager1m";
    public static final String CAFFEINE_3M = "caffeineCacheManager3m";
    public static final String CAFFEINE_5M = "caffeineCacheManager5m";
    public static final String CAFFEINE_10M = "caffeineCacheManager10m";
    public static final String CAFFEINE_30M = "caffeineCacheManager30m";
    public static final String CAFFEINE_1H = "caffeineCacheManager1h";

    /* ---- Distributed Redis managers ---- */

    public static final String REDIS_30S = "redisCacheManager30s";
    public static final String REDIS_1M = "redisCacheManager1m";
    public static final String REDIS_3M = "redisCacheManager3m";
    public static final String REDIS_5M = "redisCacheManager5m";
    public static final String REDIS_10M = "redisCacheManager10m";
    public static final String REDIS_30M = "redisCacheManager30m";
    public static final String REDIS_1H = "redisCacheManager1h";
}
