package com.gucardev.ratelimitingbucket4j.ratelimit;

import com.gucardev.ratelimitingbucket4j.ratelimit.store.InMemoryRateLimitStore;
import com.gucardev.ratelimitingbucket4j.ratelimit.store.RateLimitStore;
import com.gucardev.ratelimitingbucket4j.ratelimit.store.RedisRateLimitStore;
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.codec.StringCodec;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import tools.jackson.databind.json.JsonMapper;

@Configuration
@ConditionalOnProperty(name = "app.rate-limit.enabled", havingValue = "true", matchIfMissing = true)
class RateLimitConfig {

    @Bean
    RateLimitPolicy rateLimitPolicy(RateLimitProperties properties) {
        return new RateLimitPolicy(properties, new ClientIpResolver(properties.trustedProxies()));
    }

    @Bean
    @ConditionalOnProperty(name = "app.rate-limit.store", havingValue = "in-memory", matchIfMissing = true)
    RateLimitStore inMemoryRateLimitStore(RateLimitProperties properties) {
        return new InMemoryRateLimitStore(longestPeriod(properties));
    }

    /** Bucket4j talks to Redis through Lettuce directly: string keys, binary bucket state. */
    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(name = "app.rate-limit.store", havingValue = "redis")
    StatefulRedisConnection<String, byte[]> rateLimitRedisConnection(LettuceConnectionFactory connectionFactory) {
        // The client Spring Boot configured from spring.data.redis.* (host, port, timeout, ...).
        RedisClient client = (RedisClient) connectionFactory.getRequiredNativeClient();
        return client.connect(RedisCodec.of(StringCodec.UTF8, ByteArrayCodec.INSTANCE));
    }

    @Bean
    @ConditionalOnProperty(name = "app.rate-limit.store", havingValue = "redis")
    RateLimitStore redisRateLimitStore(StatefulRedisConnection<String, byte[]> rateLimitRedisConnection) {
        return new RedisRateLimitStore(rateLimitRedisConnection);
    }

    @Bean
    RateLimiter rateLimiter(RateLimitProperties properties, RateLimitStore store) {
        return new RateLimiter(properties.limits(), store);
    }

    @Bean
    RateLimitedAspect rateLimitedAspect(RateLimiter rateLimiter) {
        return new RateLimitedAspect(rateLimiter);
    }

    /** Only our API is limited; actuator and static content are not. */
    @Bean
    FilterRegistrationBean<RateLimitFilter> rateLimitFilter(RateLimitPolicy policy, RateLimitStore store, JsonMapper jsonMapper) {
        FilterRegistrationBean<RateLimitFilter> registration =
                new FilterRegistrationBean<>(new RateLimitFilter(policy, store, jsonMapper));
        registration.addUrlPatterns("/api/*");
        return registration;
    }

    private static Duration longestPeriod(RateLimitProperties properties) {
        return Stream.of(
                        properties.plans().values().stream().flatMap(List::stream),
                        properties.anonymous().perClient().stream(),
                        properties.anonymous().perIp().stream(),
                        properties.sharedNetworks().stream().flatMap(network -> network.perIp().stream()),
                        properties.limits().values().stream().flatMap(List::stream))
                .flatMap(limits -> limits)
                .map(RateLimitProperties.Limit::period)
                .max(Comparator.naturalOrder())
                .orElse(Duration.ofMinutes(1));
    }
}
