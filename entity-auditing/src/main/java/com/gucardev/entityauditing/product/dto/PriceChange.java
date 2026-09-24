package com.gucardev.entityauditing.product.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record PriceChange(Long revision, Instant at, String changedBy, BigDecimal price) {
}
