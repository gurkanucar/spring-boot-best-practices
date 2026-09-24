package com.gucardev.restapiintegration.product.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;

/** Body of POST (create) and PUT (full replace). Validated here, before the remote call is made. */
public record ProductRequest(
        @NotBlank String name,
        @NotNull @PositiveOrZero BigDecimal price) {
}
