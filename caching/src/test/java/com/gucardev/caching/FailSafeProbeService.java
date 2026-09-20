package com.gucardev.caching;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Test-only: proves the cache being unreachable doesn't break the call. The counter
 * lets a test assert the real method actually ran (a swallowed cache failure still
 * has to fall through to the method body, not just avoid throwing).
 */
@Component
class FailSafeProbeService {

    private final AtomicInteger invocationCount = new AtomicInteger();

    @Cacheable(cacheNames = CacheNames.USERS, cacheManager = CacheManagers.REDIS_30S)
    public String greet(String name) {
        invocationCount.incrementAndGet();
        return "Hello, " + name;
    }

    int invocationCount() {
        return invocationCount.get();
    }
}
