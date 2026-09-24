package com.gucardev.entityauditing.product.dto;

import java.time.Instant;

/** A product that no longer exists in {@code product}, reconstructed from its DELETE revision. */
public record DeletedProduct(
        Long id,
        Long revision,
        Instant deletedAt,
        String deletedBy,
        ProductSnapshot lastState) {
}
