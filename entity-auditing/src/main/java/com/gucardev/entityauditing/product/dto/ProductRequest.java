package com.gucardev.entityauditing.product.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/** Create and full update. */
public record ProductRequest(
        @NotBlank @Size(max = 100) String name,
        @NotNull @PositiveOrZero BigDecimal price,
        @PositiveOrZero int stock,
        @NotNull Long categoryId) {
}
