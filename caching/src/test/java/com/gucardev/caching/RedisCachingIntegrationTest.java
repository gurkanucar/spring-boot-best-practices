package com.gucardev.caching;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;

/**
 * Proves caching actually happens when Redis is reachable — the opposite case from
 * {@link FailSafeCachingTest}. Requires a real Redis: run {@code docker compose up -d}
 * (see docker-compose.yml) before running this test, otherwise every assertion here
 * fails for the right reason — there's nothing to cache against.
 *
 * <p>Each test uses names unique to that test run instead of fixed names ("Jane", "Bob")
 * plus a {@code clear()} in {@code @BeforeEach}. Measured directly against this project's
 * pooled Redis connections: clear()'s DEL could still be in flight on one pooled
 * connection while greet()'s own cache-lookup GET, on a different pooled connection, read
 * the about-to-be-deleted leftover value from a previous run and treated it as a hit,
 * skipping the real write entirely — then the delete landed and the key was simply gone,
 * so polling for it timed out. Unique names make that race structurally impossible: there
 * is never a previous value for a fresh key to race against, and invocation counts stay
 * meaningful per-test without needing a shared counter reset.
 */
@SpringBootTest
class RedisCachingIntegrationTest {

    @Autowired
    private FailSafeProbeService probeService;

    @Autowired
    @Qualifier(CacheManagers.REDIS_30S)
    private CacheManager redisCacheManager30s;

    private void awaitVisible(String key) {
        for (int attempt = 1; attempt <= 20; attempt++) {
            if (redisCacheManager30s.getCache(CacheNames.USERS).get(key) != null) {
                return;
            }
            sleepBriefly();
        }
        throw new IllegalStateException("Write to key '" + key + "' never became visible after 20 attempts - "
                + "is docker compose up actually running? (see docker-compose.yml)");
    }

    private static void sleepBriefly() {
        try {
            Thread.sleep(25);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Test
    void secondCallWithSameKeyIsServedFromCacheNotReinvoked() {
        String name = "Jane-" + System.nanoTime();
        int before = probeService.invocationCount();

        String first = probeService.greet(name);
        awaitVisible(name);
        String second = probeService.greet(name);

        assertThat(first).isEqualTo(second).isEqualTo("Hello, " + name);
        assertThat(probeService.invocationCount()).isEqualTo(before + 1);
    }

    @Test
    void differentKeysAreCachedIndependently() {
        String nameA = "Jane-" + System.nanoTime();
        String nameB = "Bob-" + System.nanoTime();
        int before = probeService.invocationCount();

        probeService.greet(nameA);
        awaitVisible(nameA);
        probeService.greet(nameB);
        awaitVisible(nameB);
        probeService.greet(nameA);

        assertThat(probeService.invocationCount()).isEqualTo(before + 2);
    }
}
