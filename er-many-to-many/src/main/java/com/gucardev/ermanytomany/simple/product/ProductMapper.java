package com.gucardev.ermanytomany.simple.product;

import com.gucardev.ermanytomany.simple.category.Category;
import com.gucardev.ermanytomany.simple.category.CategoryMapper;
import com.gucardev.ermanytomany.simple.product.dto.ProductRequest;
import com.gucardev.ermanytomany.simple.product.dto.ProductResponse;
import java.util.Comparator;

public final class ProductMapper {

    private ProductMapper() {
    }

    public static Product toEntity(ProductRequest request) {
        Product product = new Product();
        product.setName(request.name());
        product.setPrice(request.price());
        return product;
    }

    public static ProductResponse toResponse(Product product) {
        // Sets have no order; sort by name so responses are stable.
        return new ProductResponse(product.getId(), product.getName(), product.getPrice(),
                product.getCategories().stream()
                        .sorted(Comparator.comparing(Category::getName))
                        .map(CategoryMapper::toResponse)
                        .toList());
    }
}
