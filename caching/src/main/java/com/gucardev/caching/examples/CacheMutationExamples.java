package com.gucardev.caching.examples;

import com.gucardev.caching.CacheManagers;
import com.gucardev.caching.CacheNames;
import com.gucardev.caching.examples.dto.FakeProduct;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Component;

/**
 * {@code @CachePut}, {@code @CacheEvict} (single key / allEntries / beforeInvocation),
 * and {@code @Caching} to combine several cache operations on one method.
 */
@Component
public class CacheMutationExamples {

    private final AtomicInteger updateInvocations = new AtomicInteger();
    private final AtomicInteger riskyInvocations = new AtomicInteger();

    /**
     * Unlike {@code @Cacheable}, {@code @CachePut} always runs the method — there is no
     * lookup-and-skip. The return value replaces whatever was cached under this key. Use
     * this for an "update" operation where the write should also refresh the cache in one step.
     */
    @CachePut(cacheNames = CacheNames.EXAMPLE_PRODUCTS, cacheManager = CacheManagers.CAFFEINE_5M, key = "#id")
    public FakeProduct update(Long id, String newName) {
        updateInvocations.incrementAndGet();
        return new FakeProduct(id, newName, BigDecimal.TEN, Instant.now());
    }

    /** Evicts one key. */
    @CacheEvict(cacheNames = CacheNames.EXAMPLE_PRODUCTS, cacheManager = CacheManagers.CAFFEINE_5M, key = "#id")
    public void delete(Long id) {
    }

    /** Evicts every entry in the cache — e.g. after a bulk import invalidates everything at once. */
    @CacheEvict(cacheNames = CacheNames.EXAMPLE_PRODUCTS, cacheManager = CacheManagers.CAFFEINE_5M, allEntries = true)
    public void clearAll() {
    }

    /**
     * Default eviction timing is AFTER a successful (non-throwing) return — if this method
     * throws, the entry is NOT evicted. Contrast with {@link #deleteEvenIfItFails}.
     */
    @CacheEvict(cacheNames = CacheNames.EXAMPLE_PRODUCTS, cacheManager = CacheManagers.CAFFEINE_5M, key = "#id")
    public void deleteFailsAfterEviction(Long id) {
        riskyInvocations.incrementAndGet();
        throw new IllegalStateException("simulated failure - default afterInvocation means the entry survives this");
    }

    /**
     * {@code beforeInvocation = true} evicts BEFORE the method body runs, so the entry is
     * gone regardless of whether this then throws — appropriate when the eviction itself
     * must happen unconditionally (invalidate first, then attempt a risky operation).
     */
    @CacheEvict(cacheNames = CacheNames.EXAMPLE_PRODUCTS, cacheManager = CacheManagers.CAFFEINE_5M,
            key = "#id", beforeInvocation = true)
    public void deleteEvenIfItFails(Long id) {
        riskyInvocations.incrementAndGet();
        throw new IllegalStateException("simulated failure - beforeInvocation means the entry is already gone");
    }

    /**
     * {@code @Caching} combines cache operations that don't share one annotation type on
     * the same method — here, "renaming" a cached key: evict the old id, put the new one,
     * in a single method.
     */
    @Caching(
            evict = @CacheEvict(cacheNames = CacheNames.EXAMPLE_PRODUCTS, cacheManager = CacheManagers.CAFFEINE_5M, key = "#oldId"),
            put = @CachePut(cacheNames = CacheNames.EXAMPLE_PRODUCTS, cacheManager = CacheManagers.CAFFEINE_5M, key = "#newId")
    )
    public FakeProduct moveToNewId(Long oldId, Long newId) {
        return FakeProduct.fake(newId);
    }

    public int updateInvocations() {
        return updateInvocations.get();
    }

    public int riskyInvocations() {
        return riskyInvocations.get();
    }
}
