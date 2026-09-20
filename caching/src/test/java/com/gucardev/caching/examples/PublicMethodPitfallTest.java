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
 * Deliberately in the same package as {@link PublicMethodPitfallExamples} — that's what
 * makes calling its package-private method possible at all. See that class's javadoc for
 * why {@code packagePrivateMethodAlsoCachesUnderCglibButItsNotPortable}'s result is not
 * a mistake: this project's CGLIB proxying is why it caches, and that's exactly the trap.
 */
@SpringBootTest
class PublicMethodPitfallTest {

    @Autowired
    private PublicMethodPitfallExamples examples;

    @Autowired
    @Qualifier(CacheManagers.CAFFEINE_5M)
    private CacheManager caffeineCacheManager5m;

    @BeforeEach
    void resetState() {
        Objects.requireNonNull(caffeineCacheManager5m.getCache(CacheNames.EXAMPLE_PRODUCTS)).clear();
    }

    @Test
    void publicMethodCachesOnRepeatedCalls() {
        examples.findPublic(100L);
        examples.findPublic(100L);
        examples.findPublic(100L);

        assertThat(examples.publicInvocations()).isEqualTo(1);
    }

    @Test
    void packagePrivateMethodAlsoCachesUnderCglibButItsNotPortable() {
        examples.findPackagePrivate(200L);
        examples.findPackagePrivate(200L);
        examples.findPackagePrivate(200L);

        // This is the measured, verified CGLIB behavior — not a claim that it's safe to rely on.
        assertThat(examples.packagePrivateInvocations()).isEqualTo(1);
    }

    @Test
    void privateMethodNeverCachesUnderAnyProxyMode() {
        examples.callFindPrivate(300L);
        examples.callFindPrivate(300L);
        examples.callFindPrivate(300L);

        assertThat(examples.privateInvocations()).isEqualTo(3);
    }
}
