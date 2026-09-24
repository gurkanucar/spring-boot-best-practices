package com.gucardev.entityauditing.product.dto;

import java.math.BigDecimal;
import java.time.Instant;

/** Current state plus the Spring Data auditing columns (who created it, who changed it last). */
public record ProductResponse(
        Long id,
        String name,
        BigDecimal price,
        int stock,
        String categoryName,
        Instant createdAt,
        String createdBy,
        Instant lastModifiedAt,
        String lastModifiedBy) {
}
