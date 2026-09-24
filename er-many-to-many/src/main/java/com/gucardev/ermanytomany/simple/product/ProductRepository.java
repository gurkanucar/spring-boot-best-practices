package com.gucardev.ermanytomany.simple.product;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, Long> {

    // Single-product reads fetch the categories with a join. Paged lists do not (a fetch join on
    // a collection cannot be paginated in SQL); they rely on @BatchSize on Product.categories.
    @Override
    @EntityGraph(attributePaths = "categories")
    Optional<Product> findById(Long id);

    // Filters through the join table in the database and paginates there. The alternative,
    // category.getProducts(), would load every product of the category into memory.
    Page<Product> findByCategoriesId(Long categoryId, Pageable pageable);
}
