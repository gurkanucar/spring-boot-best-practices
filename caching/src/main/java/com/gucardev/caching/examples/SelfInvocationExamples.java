package com.gucardev.caching.examples;

import com.gucardev.caching.CacheManagers;
import com.gucardev.caching.CacheNames;
import com.gucardev.caching.examples.dto.FakeProduct;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

/**
 * Self-invocation bypasses caching entirely — same silent-failure shape as the
 * public-method rule ({@link PublicMethodPitfallExamples}), different cause: Spring's
 * proxy only intercepts a call that arrives FROM OUTSIDE the bean (another bean invoking
 * it through its injected reference). A call to {@code this.method()} — or a bare,
 * unqualified {@code method()} — from another method IN THE SAME CLASS is a plain Java
 * call on the raw object, never reaching the proxy, so the annotation on the target
 * method is simply never applied for that call path.
 *
 * <p>Three versions below, in the order you'd actually reach for them: the broken one,
 * a workaround that works but isn't the recommended fix, and the fix worth actually using.
 */
@Component
public class SelfInvocationExamples {

    private final AtomicInteger directInvocations = new AtomicInteger();
    private final ApplicationContext applicationContext;
    private final ProductLookup productLookup;

    public SelfInvocationExamples(ApplicationContext applicationContext, ProductLookup productLookup) {
        this.applicationContext = applicationContext;
        this.productLookup = productLookup;
    }

    @Cacheable(cacheNames = CacheNames.EXAMPLE_PRODUCTS, cacheManager = CacheManagers.CAFFEINE_5M)
    public FakeProduct findById(Long id) {
        directInvocations.incrementAndGet();
        return FakeProduct.fake(id);
    }

    /**
     * BROKEN: {@code this.findById(id)} calls the raw object directly, skipping the proxy —
     * this never hits the cache no matter how many times it's called with the same id.
     */
    public FakeProduct findByIdViaSelfInvocation(Long id) {
        return this.findById(id);
    }

    /**
     * WORKS, but is a workaround, not the recommended fix: resolve this bean's own
     * Spring-managed proxy from the {@link ApplicationContext} and call through THAT
     * instead of through {@code this}. Pulling in the whole context just to call yourself
     * is exactly why {@link #findByIdViaCollaborator} is the version worth actually using.
     */
    public FakeProduct findByIdViaSelfProxy(Long id) {
        SelfInvocationExamples selfProxy = applicationContext.getBean(SelfInvocationExamples.class);
        return selfProxy.findById(id);
    }

    /**
     * PREFERRED: the cached method never lived in this class to begin with — it's on the
     * injected {@link ProductLookup} collaborator, so calling it is an ordinary cross-bean
     * call that goes through ProductLookup's own proxy with no special handling required.
     */
    public FakeProduct findByIdViaCollaborator(Long id) {
        return productLookup.findById(id);
    }

    public int directInvocations() {
        return directInvocations.get();
    }

    /** Test-only: this bean is a shared singleton across every test method in a class. */
    void resetDirectInvocations() {
        directInvocations.set(0);
    }
}
