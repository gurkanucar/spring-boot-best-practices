package com.gucardev.caching.examples;

import com.gucardev.caching.CacheManagers;
import com.gucardev.caching.CacheNames;
import com.gucardev.caching.examples.dto.FakeProduct;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

/**
 * Method visibility and {@code @Cacheable}, verified empirically rather than assumed —
 * the commonly-repeated "public methods only" rule turned out to be more specific than
 * that claim usually gets stated:
 *
 * <ul>
 *   <li>{@link #findPrivate} — a PRIVATE method is never proxied by any mechanism, ever.
 *       Private methods aren't visible to a subclass at all, so CGLIB can't override
 *       them and a JDK interface proxy could never have declared one in the first place.
 *       This is the one case the rule is unconditionally true for.</li>
 *   <li>{@link #findPackagePrivate} — this class implements no interface, so Spring MUST
 *       use a CGLIB subclass proxy here (JDK dynamic proxies need an interface to work
 *       from at all). CGLIB generates that subclass in the SAME package as the original
 *       class specifically so it CAN override package-private methods — and measured
 *       here, it does: this method caches correctly. That is proxy-mode-specific and
 *       not something to rely on: switch this bean to implement an interface (or force
 *       {@code spring.aop.proxy-target-class=false} in a mixed setup) and this exact
 *       method would silently stop caching with no code change to itself. "Public" stays
 *       the right practice because it's the one visibility that works under every
 *       proxying strategy, not just the one Spring Boot happens to default to here.</li>
 *   <li>{@link #findPublic} — works under every proxy mode, no caveats.</li>
 * </ul>
 *
 * See PublicMethodPitfallTest for the actual measured invocation counts.
 */
@Component
public class PublicMethodPitfallExamples {

    private final AtomicInteger publicInvocations = new AtomicInteger();
    private final AtomicInteger packagePrivateInvocations = new AtomicInteger();
    private final AtomicInteger privateInvocations = new AtomicInteger();

    @Cacheable(cacheNames = CacheNames.EXAMPLE_PRODUCTS, cacheManager = CacheManagers.CAFFEINE_5M)
    public FakeProduct findPublic(Long id) {
        publicInvocations.incrementAndGet();
        return FakeProduct.fake(id);
    }

    /** Caches under this project's CGLIB proxying — see the class javadoc for why that's not portable. */
    @Cacheable(cacheNames = CacheNames.EXAMPLE_PRODUCTS, cacheManager = CacheManagers.CAFFEINE_5M)
    FakeProduct findPackagePrivate(Long id) {
        packagePrivateInvocations.incrementAndGet();
        return FakeProduct.fake(id);
    }

    /** Never caches, unconditionally — private methods can't be overridden by any proxy. */
    @Cacheable(cacheNames = CacheNames.EXAMPLE_PRODUCTS, cacheManager = CacheManagers.CAFFEINE_5M)
    private FakeProduct findPrivate(Long id) {
        privateInvocations.incrementAndGet();
        return FakeProduct.fake(id);
    }

    /** Only exists to call the private method from outside — a real private method can't be called externally at all. */
    public FakeProduct callFindPrivate(Long id) {
        return findPrivate(id);
    }

    public int publicInvocations() {
        return publicInvocations.get();
    }

    public int packagePrivateInvocations() {
        return packagePrivateInvocations.get();
    }

    public int privateInvocations() {
        return privateInvocations.get();
    }
}
