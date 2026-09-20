package com.gucardev.caching;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Proves the fail-safe wiring against a deliberately unreachable Redis — a fixed
 * bad host/port override, not "whatever happens to be running locally". This must
 * pass whether or not {@code docker compose up} has been run; see
 * {@link RedisCachingIntegrationTest} for the opposite case, which requires it.
 */
@SpringBootTest(properties = {
        "spring.data.redis.host=localhost",
        "spring.data.redis.port=1"
})
class FailSafeCachingTest {

    @Autowired
    private FailSafeProbeService probeService;

    @Test
    void cacheableMethodStillRunsWhenRedisIsUnreachable() {
        assertThatNoException().isThrownBy(() -> {
            String result = probeService.greet("Jane");
            assertThat(result).isEqualTo("Hello, Jane");
        });

        // A failed GET falls through to a real invocation every time (no caching happened),
        // which is the fail-safe behavior itself, not a bug in this test.
        assertThat(probeService.invocationCount()).isEqualTo(1);

        assertThatNoException().isThrownBy(() -> probeService.greet("Jane"));
        assertThat(probeService.invocationCount()).isEqualTo(2);
    }
}
