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

/** The three findByIdVia* methods each demonstrate one point from SelfInvocationExamples' javadoc. */
@SpringBootTest
class SelfInvocationTest {

    @Autowired
    private SelfInvocationExamples examples;

    @Autowired
    private ProductLookup productLookup;

    @Autowired
    @Qualifier(CacheManagers.CAFFEINE_5M)
    private CacheManager caffeineCacheManager5m;

    @BeforeEach
    void resetState() {
        Objects.requireNonNull(caffeineCacheManager5m.getCache(CacheNames.EXAMPLE_PRODUCTS)).clear();
        examples.resetDirectInvocations();
        productLookup.resetInvocations();
    }

    @Test
    void callingDirectlyFromOutsideCachesNormally() {
        examples.findById(1L);
        examples.findById(1L);

        assertThat(examples.directInvocations()).isEqualTo(1);
    }

    @Test
    void selfInvocationNeverCachesNoMatterHowManyTimesItsCalled() {
        examples.findByIdViaSelfInvocation(1L);
        examples.findByIdViaSelfInvocation(1L);
        examples.findByIdViaSelfInvocation(1L);

        // Every call reached the real method body — the proxy was never involved.
        assertThat(examples.directInvocations()).isEqualTo(3);
    }

    @Test
    void selfProxyWorkaroundDoesCacheCorrectly() {
        examples.findByIdViaSelfProxy(1L);
        examples.findByIdViaSelfProxy(1L);

        assertThat(examples.directInvocations()).isEqualTo(1);
    }

    @Test
    void collaboratorFixDoesCacheCorrectly() {
        examples.findByIdViaCollaborator(1L);
        examples.findByIdViaCollaborator(1L);

        assertThat(productLookup.invocations()).isEqualTo(1);
    }
}
