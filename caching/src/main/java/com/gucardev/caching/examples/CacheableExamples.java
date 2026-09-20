package com.gucardev.caching.examples;

import com.gucardev.caching.CacheManagers;
import com.gucardev.caching.CacheNames;
import com.gucardev.caching.examples.dto.FakeProduct;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

/**
 * {@code @Cacheable} variants: default key, explicit SpEL key, {@code condition},
 * {@code unless}, {@code sync}. Every method here is deliberately {@code public} —
 * caching annotations are silently ignored on package-private/protected methods under
 * Spring's default CGLIB proxy (the annotation just does nothing, no error, no warning).
 *
 * <p>Each *Invocations counter lets a test/caller tell "was the real method body reached"
 * apart from "was this served from cache" — the return value alone can't tell you that.
 */
@Component
public class CacheableExamples {

    private final AtomicInteger basicInvocations = new AtomicInteger();
    private final AtomicInteger compositeKeyInvocations = new AtomicInteger();
    private final AtomicInteger conditionInvocations = new AtomicInteger();
    private final AtomicInteger unlessInvocations = new AtomicInteger();
    private final AtomicInteger syncInvocations = new AtomicInteger();

    /** Default key generation: SimpleKeyGenerator uses the single argument itself as the key. */
    @Cacheable(cacheNames = CacheNames.EXAMPLE_PRODUCTS, cacheManager = CacheManagers.CAFFEINE_5M)
    public FakeProduct findById(Long id) {
        basicInvocations.incrementAndGet();
        return FakeProduct.fake(id);
    }

    /**
     * SpEL composite key: two arguments only produce one entry when combined this way —
     * without an explicit key, SimpleKeyGenerator would hash both arguments together
     * into one opaque key, which works but isn't inspectable/loggable the way this is.
     */
    @Cacheable(cacheNames = CacheNames.EXAMPLE_PRODUCTS, cacheManager = CacheManagers.CAFFEINE_5M,
            key = "#region + ':' + #id")
    public FakeProduct findByRegionAndId(String region, Long id) {
        compositeKeyInvocations.incrementAndGet();
        return FakeProduct.fake(id);
    }

    /**
     * {@code condition} is evaluated BEFORE the method runs, on the ARGUMENTS — it decides
     * whether caching applies to this call at all (both the lookup and the write are
     * skipped when false, so the method runs fresh every time). Here, a non-positive id
     * is treated as a "don't bother caching this" sentinel.
     */
    @Cacheable(cacheNames = CacheNames.EXAMPLE_PRODUCTS, cacheManager = CacheManagers.CAFFEINE_5M,
            condition = "#id > 0")
    public FakeProduct findIfPositiveId(Long id) {
        conditionInvocations.incrementAndGet();
        return FakeProduct.fake(id);
    }

    /**
     * {@code unless} is evaluated AFTER the method runs, on the RESULT — the lookup still
     * happens, but a result matching the expression is never written to the cache. Here,
     * a null result (nothing found) is never cached, so a later real value isn't shadowed
     * by a stale "not found" from before the underlying data existed.
     */
    @Cacheable(cacheNames = CacheNames.EXAMPLE_PRODUCTS, cacheManager = CacheManagers.CAFFEINE_5M,
            unless = "#result == null")
    public FakeProduct findOrNull(Long id) {
        unlessInvocations.incrementAndGet();
        return id < 0 ? null : FakeProduct.fake(id);
    }

    /**
     * {@code sync = true}: only one thread computes a given key at a time; concurrent
     * callers for the SAME key block and share the first thread's result instead of all
     * recomputing it in parallel ("thundering herd" / "cache stampede" protection). Note
     * from {@code CacheErrorHandler}'s own javadoc: with {@code sync = true} there is only
     * one combined get step, not a separate get-then-put — a get failure here has no
     * follow-up put attempt to also fail.
     */
    @Cacheable(cacheNames = CacheNames.EXAMPLE_PRODUCTS, cacheManager = CacheManagers.CAFFEINE_5M, sync = true)
    public FakeProduct findSynchronized(Long id) {
        syncInvocations.incrementAndGet();
        return FakeProduct.fake(id);
    }

    public int basicInvocations() {
        return basicInvocations.get();
    }

    public int compositeKeyInvocations() {
        return compositeKeyInvocations.get();
    }

    public int conditionInvocations() {
        return conditionInvocations.get();
    }

    public int unlessInvocations() {
        return unlessInvocations.get();
    }

    public int syncInvocations() {
        return syncInvocations.get();
    }

    /** Test-only: this bean is a shared singleton across every test method in a class. */
    void resetInvocationCounts() {
        basicInvocations.set(0);
        compositeKeyInvocations.set(0);
        conditionInvocations.set(0);
        unlessInvocations.set(0);
        syncInvocations.set(0);
    }
}
