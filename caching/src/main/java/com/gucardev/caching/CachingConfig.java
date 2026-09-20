package com.gucardev.caching;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Configuration;

/**
 * Enables Spring's annotation-driven cache management. The actual cache managers are
 * declared in {@link CaffeineCacheConfig} and {@link RedisCacheConfig}.
 */
@Configuration
@EnableCaching
public class CachingConfig {
}
