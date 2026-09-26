package com.gucardev.ratelimitingbucket4j.ratelimit.store;

import static org.assertj.core.api.Assertions.assertThat;

import com.gucardev.ratelimitingbucket4j.ratelimit.RateLimitBucket;
import com.gucardev.ratelimitingbucket4j.ratelimit.RateLimitProperties.Limit;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.codec.StringCodec;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** The shared (multi-instance) store against a real Redis. Skipped when Docker is not running. */
@Testcontainers(disabledWithoutDocker = true)
class RedisRateLimitStoreTest {

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:8.8-alpine").withExposedPorts(6379);

    static RedisClient client;
    // Two stores with their own connections: two instances of the app behind a load balancer.
    static RedisRateLimitStore instanceA;
    static RedisRateLimitStore instanceB;

    @BeforeAll
    static void connect() {
        client = RedisClient.create(RedisURI.create(REDIS.getHost(), REDIS.getMappedPort(6379)));
        instanceA = new RedisRateLimitStore(connection());
        instanceB = new RedisRateLimitStore(connection());
    }

    @AfterAll
    static void disconnect() {
        client.shutdown();
    }

    @Test
    void allInstancesShareOneBucket() {
        RateLimitBucket bucket = new RateLimitBucket("client:shared", List.of(new Limit(3, Duration.ofSeconds(30))));

        assertThat(instanceA.tryConsume(bucket, 1).remaining()).isEqualTo(2);
        assertThat(instanceB.tryConsume(bucket, 1).remaining()).isEqualTo(1);
        assertThat(instanceA.tryConsume(bucket, 1).remaining()).isEqualTo(0);

        RateLimitStore.Decision rejected = instanceB.tryConsume(bucket, 1);
        assertThat(rejected.allowed()).isFalse();
        // 3 tokens per 30 s come back one by one: the next one within 10 s, not at the end of a window.
        assertThat(rejected.retryAfter()).isPositive().isLessThanOrEqualTo(Duration.ofSeconds(10));
    }

    @Test
    void everyLimitMustHaveEnoughTokens() {
        RateLimitBucket bucket = new RateLimitBucket("client:two-limits",
                List.of(new Limit(100, Duration.ofMinutes(1)), new Limit(2, Duration.ofSeconds(1))));

        assertThat(instanceA.tryConsume(bucket, 1).allowed()).isTrue();
        assertThat(instanceB.tryConsume(bucket, 1).allowed()).isTrue();
        // Plenty left per minute, but the per-second limit is empty.
        assertThat(instanceA.tryConsume(bucket, 1).allowed()).isFalse();
    }

    @Test
    void requestCostingMoreThanTheCapacityGetsABoundedRetryAfter() {
        RateLimitBucket bucket = new RateLimitBucket("client:too-expensive", List.of(new Limit(3, Duration.ofSeconds(30))));

        RateLimitStore.Decision decision = instanceA.tryConsume(bucket, 10);

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.retryAfter()).isEqualTo(Duration.ofSeconds(30));
    }

    private static StatefulRedisConnection<String, byte[]> connection() {
        return client.connect(RedisCodec.of(StringCodec.UTF8, ByteArrayCodec.INSTANCE));
    }
}
