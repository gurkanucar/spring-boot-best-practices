package com.gucardev.caching.examples.dto;

import java.math.BigDecimal;
import java.time.Instant;

/** Fake data only — no repository behind this, just enough to see cache hits vs misses. */
public record FakeProduct(Long id, String name, BigDecimal price, Instant fetchedAt) {

    public static FakeProduct fake(Long id) {
        return new FakeProduct(id, "Product " + id, new BigDecimal("9.99"), Instant.now());
    }
}
