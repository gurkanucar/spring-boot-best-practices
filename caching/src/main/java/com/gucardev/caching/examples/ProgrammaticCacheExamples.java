package com.gucardev.caching.examples;

import com.gucardev.caching.CacheManagers;
import com.gucardev.caching.CacheNames;
import com.gucardev.caching.examples.dto.FakeProduct;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

/**
 * Direct {@link Cache}/{@link CacheManager} API, no annotations — useful when the caching
 * decision is too dynamic for SpEL ({@code condition}/{@code unless}), or when a call site
 * genuinely needs "look up without computing" rather than {@code @Cacheable}'s
 * always-compute-on-miss behavior.
 */
@Component
public class ProgrammaticCacheExamples {

    private final Cache cache;

    public ProgrammaticCacheExamples(@Qualifier(CacheManagers.CAFFEINE_5M) CacheManager cacheManager) {
        this.cache = cacheManager.getCache(CacheNames.EXAMPLE_PRODUCTS);
    }

    /** Returns null on a miss — no automatic "compute and store" the way {@code @Cacheable} has. */
    public FakeProduct getIfPresent(Long id) {
        Cache.ValueWrapper wrapper = cache.get(id);
        return wrapper == null ? null : (FakeProduct) wrapper.get();
    }

    public void put(FakeProduct product) {
        cache.put(product.id(), product);
    }

    public void evict(Long id) {
        cache.evict(id);
    }

    /**
     * {@code Cache.get(key, Callable)} is the programmatic equivalent of
     * {@code @Cacheable(sync = true)} — compute-if-absent, same single-flight guarantee
     * for concurrent callers on the same key.
     */
    public FakeProduct getOrCompute(Long id) {
        return cache.get(id, () -> FakeProduct.fake(id));
    }
}
