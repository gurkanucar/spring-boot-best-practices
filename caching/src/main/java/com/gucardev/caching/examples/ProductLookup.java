package com.gucardev.caching.examples;

import com.gucardev.caching.CacheManagers;
import com.gucardev.caching.CacheNames;
import com.gucardev.caching.examples.dto.FakeProduct;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

/**
 * The PREFERRED fix for self-invocation (see {@link SelfInvocationExamples}): move the
 * cached method into its own collaborator bean. Any other bean calling this one always
 * goes through the proxy naturally — there's no "self" call path to accidentally bypass,
 * and no {@code ApplicationContext} lookup needed.
 */
@Component
public class ProductLookup {

    private final AtomicInteger invocations = new AtomicInteger();

    @Cacheable(cacheNames = CacheNames.EXAMPLE_PRODUCTS, cacheManager = CacheManagers.CAFFEINE_5M)
    public FakeProduct findById(Long id) {
        invocations.incrementAndGet();
        return FakeProduct.fake(id);
    }

    public int invocations() {
        return invocations.get();
    }

    /** Test-only: this bean is a shared singleton across every test method in a class. */
    void resetInvocations() {
        invocations.set(0);
    }
}
