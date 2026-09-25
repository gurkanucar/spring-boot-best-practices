package com.gucardev.resillience4j.ratelimit;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.json.JsonMapper;

@Configuration
@ConditionalOnProperty(name = "app.rate-limit.enabled", havingValue = "true", matchIfMissing = true)
class RateLimitConfig {

    @Bean
    RateLimitPolicy rateLimitPolicy(RateLimitProperties properties) {
        return new RateLimitPolicy(properties, new ClientIpResolver(properties.trustedProxies()));
    }

    @Bean
    @ConditionalOnProperty(name = "app.rate-limit.store", havingValue = "redis")
    RateLimitStore redisRateLimitStore(StringRedisTemplate redis) {
        return new RedisRateLimitStore(redis);
    }

    @Bean
    @ConditionalOnProperty(name = "app.rate-limit.store", havingValue = "in-memory", matchIfMissing = true)
    RateLimitStore inMemoryRateLimitStore() {
        return new InMemoryRateLimitStore();
    }

    /** Only our API is limited; the fake third-party APIs and actuator are not. */
    @Bean
    FilterRegistrationBean<RateLimitFilter> rateLimitFilter(RateLimitPolicy policy, RateLimitStore store, JsonMapper jsonMapper) {
        FilterRegistrationBean<RateLimitFilter> registration =
                new FilterRegistrationBean<>(new RateLimitFilter(policy, store, jsonMapper));
        registration.addUrlPatterns("/api/*");
        return registration;
    }
}
