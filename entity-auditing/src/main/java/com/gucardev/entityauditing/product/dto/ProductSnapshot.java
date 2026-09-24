package com.gucardev.entityauditing.product.dto;

import java.math.BigDecimal;

/** The product as it was in one revision, including the category name at that time. */
public record ProductSnapshot(Long id, String name, BigDecimal price, int stock, Long categoryId, String categoryName) {
}
