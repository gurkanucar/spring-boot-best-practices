package com.gucardev.restapiintegration.product.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;

/**
 * Body of PATCH: only the fields to change. {@code NON_NULL} leaves unset fields out of the JSON,
 * so the remote API receives {@code {"price":9.9}} instead of {@code {"name":null,"price":9.9}}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProductPatchRequest(
        String name,
        @PositiveOrZero BigDecimal price) {
}
