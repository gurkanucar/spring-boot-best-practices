package com.gucardev.caching.examples;

import static org.assertj.core.api.Assertions.assertThat;

import com.gucardev.caching.CacheManagers;
import com.gucardev.caching.CacheNames;
import com.gucardev.caching.examples.dto.FakeProduct;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;

@SpringBootTest
class ProgrammaticCacheExamplesTest {

    @Autowired
    private ProgrammaticCacheExamples examples;

    @Autowired
    @Qualifier(CacheManagers.CAFFEINE_5M)
    private CacheManager caffeineCacheManager5m;

    @BeforeEach
    void resetState() {
        Objects.requireNonNull(caffeineCacheManager5m.getCache(CacheNames.EXAMPLE_PRODUCTS)).clear();
    }

    @Test
    void getIfPresentReturnsNullOnAMissRatherThanComputing() {
        assertThat(examples.getIfPresent(1L)).isNull();
    }

    @Test
    void putThenGetIfPresentReturnsTheStoredValue() {
        FakeProduct product = new FakeProduct(1L, "Manual", BigDecimal.ONE, Instant.now());

        examples.put(product);

        assertThat(examples.getIfPresent(1L)).isEqualTo(product);
    }

    @Test
    void evictRemovesAPreviouslyPutValue() {
        examples.put(new FakeProduct(1L, "Manual", BigDecimal.ONE, Instant.now()));

        examples.evict(1L);

        assertThat(examples.getIfPresent(1L)).isNull();
    }

    @Test
    void getOrComputeCachesLikeCacheableDoes() {
        FakeProduct first = examples.getOrCompute(1L);
        FakeProduct second = examples.getOrCompute(1L);

        assertThat(first).isEqualTo(second);
    }
}
