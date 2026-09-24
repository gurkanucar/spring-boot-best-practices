package com.gucardev.ermanytomany.simple.product.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.util.Set;

/**
 * {@code categoryIds} is the complete set of categories the product belongs to. On create it is
 * optional; on PUT it replaces the previous set (omitted or empty means "no categories").
 */
public record ProductRequest(

        @NotBlank(message = "must not be blank")
        String name,

        @NotNull(message = "must not be null")
        @Positive(message = "must be positive")
        @Digits(integer = 10, fraction = 2, message = "must have at most 10 integer and 2 fraction digits")
        BigDecimal price,

        Set<Long> categoryIds) {
}
