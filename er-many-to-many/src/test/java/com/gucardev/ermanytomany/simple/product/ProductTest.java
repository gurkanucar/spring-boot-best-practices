package com.gucardev.ermanytomany.simple.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gucardev.ermanytomany.simple.category.Category;
import org.junit.jupiter.api.Test;

class ProductTest {

    @Test
    void addCategoryUpdatesBothSides() {
        Product product = new Product();
        Category category = new Category();

        product.addCategory(category);

        assertThat(product.getCategories()).containsExactly(category);
        assertThat(category.getProducts()).containsExactly(product);
    }

    @Test
    void removeCategoryUpdatesBothSides() {
        Product product = new Product();
        Category category = new Category();
        product.addCategory(category);

        product.removeCategory(category);

        assertThat(product.getCategories()).isEmpty();
        assertThat(category.getProducts()).isEmpty();
    }

    @Test
    void collectionsAreNotModifiableFromOutside() {
        assertThatThrownBy(() -> new Product().getCategories().add(new Category()))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> new Category().getProducts().add(new Product()))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
