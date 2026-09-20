package com.gucardev.caching.examples;

import static org.assertj.core.api.Assertions.assertThat;

import com.gucardev.caching.CacheManagers;
import com.gucardev.caching.CacheNames;
import java.util.Objects;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;

/**
 * All-Caffeine (in-memory), so no external infra needed and no timing flakiness.
 *
 * <p>All five example methods share {@code CacheNames.EXAMPLE_PRODUCTS} with a
 * default/id-based key, so a value cached by one method's test is visible to another
 * method's test using the same id — clearing the cache (and the shared invocation
 * counters, since {@code CacheableExamples} is a singleton) before every test is what
 * keeps these order-independent.
 */
@SpringBootTest
class CacheableExamplesTest {

    @Autowired
    private CacheableExamples examples;

    @Autowired
    @Qualifier(CacheManagers.CAFFEINE_5M)
    private CacheManager caffeineCacheManager5m;

    @BeforeEach
    void resetState() {
        Objects.requireNonNull(caffeineCacheManager5m.getCache(CacheNames.EXAMPLE_PRODUCTS)).clear();
        examples.resetInvocationCounts();
    }

    @Test
    void secondCallWithSameArgIsServedFromCache() {
        examples.findById(1L);
        examples.findById(1L);

        assertThat(examples.basicInvocations()).isEqualTo(1);
    }

    @Test
    void compositeKeyTreatsDifferentRegionsAsDifferentEntries() {
        examples.findByRegionAndId("eu", 1L);
        examples.findByRegionAndId("us", 1L);
        examples.findByRegionAndId("eu", 1L);

        assertThat(examples.compositeKeyInvocations()).isEqualTo(2);
    }

    @Test
    void conditionSkipsCachingForNonPositiveIds() {
        examples.findIfPositiveId(-1L);
        examples.findIfPositiveId(-1L);
        examples.findIfPositiveId(-1L);

        assertThat(examples.conditionInvocations()).isEqualTo(3);
    }

    @Test
    void conditionStillCachesForPositiveIds() {
        examples.findIfPositiveId(1L);
        examples.findIfPositiveId(1L);

        assertThat(examples.conditionInvocations()).isEqualTo(1);
    }

    @Test
    void unlessNeverCachesANullResult() {
        examples.findOrNull(-1L);
        examples.findOrNull(-1L);

        assertThat(examples.unlessInvocations()).isEqualTo(2);
    }

    @Test
    void unlessCachesANonNullResult() {
        examples.findOrNull(1L);
        examples.findOrNull(1L);

        assertThat(examples.unlessInvocations()).isEqualTo(1);
    }

    @Test
    void syncStillCachesLikeAnyOtherCacheable() {
        examples.findSynchronized(1L);
        examples.findSynchronized(1L);
        examples.findSynchronized(1L);

        assertThat(examples.syncInvocations()).isEqualTo(1);
    }
}
