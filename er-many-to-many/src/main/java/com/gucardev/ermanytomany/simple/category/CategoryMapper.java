package com.gucardev.ermanytomany.simple.category;

import com.gucardev.ermanytomany.simple.category.dto.CategoryRequest;
import com.gucardev.ermanytomany.simple.category.dto.CategoryResponse;

public final class CategoryMapper {

    private CategoryMapper() {
    }

    public static Category toEntity(CategoryRequest request) {
        Category category = new Category();
        category.setName(request.name());
        return category;
    }

    public static CategoryResponse toResponse(Category category) {
        return new CategoryResponse(category.getId(), category.getName());
    }
}
