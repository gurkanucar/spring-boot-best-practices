package com.gucardev.resillience4j.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** The shared (multi-instance) store against a real Redis. Skipped when Docker is not running. */
@Testcontainers(disabledWithoutDocker = true)
class RedisRateLimitStoreTest {

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    static LettuceConnectionFactory connectionFactory;
    static RedisRateLimitStore store;

    @BeforeAll
    static void connect() {
        connectionFactory = new LettuceConnectionFactory(
                new RedisStandaloneConfiguration(REDIS.getHost(), REDIS.getMappedPort(6379)));
        connectionFactory.afterPropertiesSet();
        connectionFactory.start();
        store = new RedisRateLimitStore(new StringRedisTemplate(connectionFactory));
    }

    @AfterAll
    static void disconnect() {
        connectionFactory.destroy();
    }

    @Test
    void allowsTheLimitThenRejectsUntilTheWindowEnds() {
        RateLimitBucket bucket = new RateLimitBucket("client:test-a", new RateLimitProperties.Limit(3, Duration.ofSeconds(30)));

        for (int remaining = 2; remaining >= 0; remaining--) {
            RateLimitStore.Decision decision = store.tryConsume(bucket);
            assertThat(decision.allowed()).isTrue();
            assertThat(decision.remaining()).isEqualTo(remaining);
        }
        RateLimitStore.Decision rejected = store.tryConsume(bucket);
        assertThat(rejected.allowed()).isFalse();
        assertThat(rejected.retryAfter()).isPositive().isLessThanOrEqualTo(Duration.ofSeconds(30));
    }

    @Test
    void twoAppInstancesShareTheSameCounter() {
        RedisRateLimitStore otherInstance = new RedisRateLimitStore(new StringRedisTemplate(connectionFactory));
        RateLimitBucket bucket = new RateLimitBucket("client:test-b", new RateLimitProperties.Limit(2, Duration.ofSeconds(30)));

        assertThat(store.tryConsume(bucket).allowed()).isTrue();
        assertThat(otherInstance.tryConsume(bucket).allowed()).isTrue();
        assertThat(store.tryConsume(bucket).allowed()).isFalse();
    }

    @Test
    void windowResetsAfterThePeriod() throws InterruptedException {
        RateLimitBucket bucket = new RateLimitBucket("client:test-c", new RateLimitProperties.Limit(1, Duration.ofMillis(300)));

        assertThat(store.tryConsume(bucket).allowed()).isTrue();
        assertThat(store.tryConsume(bucket).allowed()).isFalse();
        Thread.sleep(400);
        assertThat(store.tryConsume(bucket).allowed()).isTrue();
    }
}
