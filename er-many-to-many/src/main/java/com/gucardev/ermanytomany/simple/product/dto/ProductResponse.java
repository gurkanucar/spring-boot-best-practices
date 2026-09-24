package com.gucardev.ermanytomany.simple.product.dto;

import com.gucardev.ermanytomany.simple.category.dto.CategoryResponse;
import java.math.BigDecimal;
import java.util.List;

public record ProductResponse(Long id, String name, BigDecimal price, List<CategoryResponse> categories) {
}
