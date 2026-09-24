package com.gucardev.restapiintegration.product.dto;

import java.math.BigDecimal;

/** The product as the remote API returns it. */
public record ProductDto(Long id, String name, BigDecimal price) {
}
