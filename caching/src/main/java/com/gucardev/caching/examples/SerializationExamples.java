package com.gucardev.caching.examples;

import com.gucardev.caching.CacheManagers;
import com.gucardev.caching.CacheNames;
import com.gucardev.caching.examples.dto.FakeOrder;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

/**
 * Proves the "confused nested object" question directly: a genuinely complex, nested,
 * polymorphic payload round-tripping through Redis correctly. This only exercises the
 * Redis manager — Caffeine holds the live Java object in memory and never serializes
 * anything, so there's nothing to "confuse" there; the serialization concern is
 * Redis-specific. See {@code RedisCacheConfig}'s javadoc for why this needs a scoped
 * {@code PolymorphicTypeValidator}, not the default (off) or unsafe (unrestricted) option.
 */
@Component
public class SerializationExamples {

    @Cacheable(cacheNames = CacheNames.EXAMPLE_ORDERS, cacheManager = CacheManagers.REDIS_30S)
    public FakeOrder findOrder(String key) {
        return key.startsWith("express") ? FakeOrder.fakeExpress() : FakeOrder.fakeStandard();
    }
}
