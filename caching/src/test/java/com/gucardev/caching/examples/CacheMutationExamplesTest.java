package com.gucardev.caching.examples;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gucardev.caching.CacheManagers;
import com.gucardev.caching.CacheNames;
import com.gucardev.caching.examples.dto.FakeProduct;
import java.util.Objects;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

@SpringBootTest
class CacheMutationExamplesTest {

    @Autowired
    private CacheMutationExamples examples;

    @Autowired
    @Qualifier(CacheManagers.CAFFEINE_5M)
    private CacheManager caffeineCacheManager5m;

    private Cache cache;

    @BeforeEach
    void resetState() {
        cache = Objects.requireNonNull(caffeineCacheManager5m.getCache(CacheNames.EXAMPLE_PRODUCTS));
        cache.clear();
    }

    @Test
    void cachePutAlwaysRunsAndOverwritesTheEntry() {
        cache.put(1L, FakeProduct.fake(1L));

        FakeProduct updated = examples.update(1L, "New Name");

        assertThat(updated.name()).isEqualTo("New Name");
        assertThat(((FakeProduct) Objects.requireNonNull(cache.get(1L)).get()).name()).isEqualTo("New Name");
    }

    @Test
    void cacheEvictRemovesOneKey() {
        cache.put(1L, FakeProduct.fake(1L));
        cache.put(2L, FakeProduct.fake(2L));

        examples.delete(1L);

        assertThat(cache.get(1L)).isNull();
        assertThat(cache.get(2L)).isNotNull();
    }

    @Test
    void cacheEvictAllEntriesClearsEverything() {
        cache.put(1L, FakeProduct.fake(1L));
        cache.put(2L, FakeProduct.fake(2L));

        examples.clearAll();

        assertThat(cache.get(1L)).isNull();
        assertThat(cache.get(2L)).isNull();
    }

    @Test
    void defaultAfterInvocationEvictionSurvivesAThrownException() {
        cache.put(99L, FakeProduct.fake(99L));

        assertThatThrownBy(() -> examples.deleteFailsAfterEviction(99L))
                .isInstanceOf(IllegalStateException.class);

        // The method threw before returning, so afterInvocation eviction never ran.
        assertThat(cache.get(99L)).isNotNull();
    }

    @Test
    void beforeInvocationEvictionHappensEvenThoughTheMethodThrows() {
        cache.put(99L, FakeProduct.fake(99L));

        assertThatThrownBy(() -> examples.deleteEvenIfItFails(99L))
                .isInstanceOf(IllegalStateException.class);

        // Evicted before the method body ran, so the exception afterward doesn't undo it.
        assertThat(cache.get(99L)).isNull();
    }

    @Test
    void cachingCombinesEvictAndPutInOneCall() {
        cache.put(1L, FakeProduct.fake(1L));

        examples.moveToNewId(1L, 2L);

        assertThat(cache.get(1L)).isNull();
        assertThat(cache.get(2L)).isNotNull();
    }
}
