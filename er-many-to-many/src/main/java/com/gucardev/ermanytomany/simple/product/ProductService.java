package com.gucardev.ermanytomany.simple.product;

import com.gucardev.ermanytomany.common.error.ResourceNotFoundException;
import com.gucardev.ermanytomany.simple.category.Category;
import com.gucardev.ermanytomany.simple.category.CategoryService;
import com.gucardev.ermanytomany.simple.product.dto.ProductRequest;
import com.gucardev.ermanytomany.simple.product.dto.ProductResponse;
import java.util.ArrayList;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryService categoryService;

    @Transactional
    public ProductResponse create(ProductRequest request) {
        Set<Category> categories = categoryService.getEntities(request.categoryIds());
        Product product = ProductMapper.toEntity(request);
        categories.forEach(product::addCategory);
        return ProductMapper.toResponse(productRepository.save(product));
    }

    @Transactional(readOnly = true)
    public ProductResponse get(Long id) {
        return ProductMapper.toResponse(getEntity(id));
    }

    /** Optional filter: only products that belong to the given category. */
    @Transactional(readOnly = true)
    public Page<ProductResponse> list(Long categoryId, Pageable pageable) {
        Page<Product> page = categoryId == null
                ? productRepository.findAll(pageable)
                : productRepository.findByCategoriesId(categoryId, pageable);
        return page.map(ProductMapper::toResponse);
    }

    /** Replaces name, price and the whole category set. */
    @Transactional
    public ProductResponse update(Long id, ProductRequest request) {
        Product product = getEntity(id);
        product.setName(request.name());
        product.setPrice(request.price());

        Set<Category> target = categoryService.getEntities(request.categoryIds());
        // Diff against the current set so Hibernate only deletes/inserts the changed join rows.
        new ArrayList<>(product.getCategories()).stream()
                .filter(current -> !target.contains(current))
                .forEach(product::removeCategory);
        target.stream()
                .filter(wanted -> !product.getCategories().contains(wanted))
                .forEach(product::addCategory);
        return ProductMapper.toResponse(product);
    }

    /** Deleting the owning side removes its join rows automatically; categories are untouched. */
    @Transactional
    public void delete(Long id) {
        productRepository.delete(getEntity(id));
    }

    /** Idempotent: adding a category the product already has changes nothing. */
    @Transactional
    public ProductResponse addCategory(Long productId, Long categoryId) {
        Product product = getEntity(productId);
        Category category = categoryService.getEntity(categoryId);
        if (!product.getCategories().contains(category)) {
            product.addCategory(category);
        }
        return ProductMapper.toResponse(product);
    }

    @Transactional
    public void removeCategory(Long productId, Long categoryId) {
        Product product = getEntity(productId);
        Category category = product.getCategories().stream()
                .filter(c -> c.getId().equals(categoryId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Product " + productId + " is not in category " + categoryId));
        product.removeCategory(category);
    }

    @Transactional(readOnly = true)
    public Product getEntity(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + id));
    }
}
