package com.gucardev.entityauditing.product;

import com.gucardev.entityauditing.category.Category;
import com.gucardev.entityauditing.category.CategoryRepository;
import com.gucardev.entityauditing.common.error.ResourceNotFoundException;
import com.gucardev.entityauditing.product.dto.ProductRequest;
import com.gucardev.entityauditing.product.dto.ProductResponse;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Plain CRUD. There is no auditing code here at all: Envers listens to Hibernate's insert,
 * update and delete events and writes the history in the same transaction.
 */
@Service
@Transactional
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;

    public ProductService(ProductRepository productRepository, CategoryRepository categoryRepository) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
    }

    public ProductResponse create(ProductRequest request) {
        Product product = new Product(request.name(), request.price(), request.stock(), category(request.categoryId()));
        return toResponse(productRepository.saveAndFlush(product));
    }

    @Transactional(readOnly = true)
    public ProductResponse get(Long id) {
        return toResponse(find(id));
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> list() {
        return productRepository.findAll().stream().map(ProductService::toResponse).toList();
    }

    /** Sending the same values again changes nothing, so Envers writes no revision. */
    public ProductResponse update(Long id, ProductRequest request) {
        Product product = find(id);
        product.update(request.name(), request.price(), request.stock(), category(request.categoryId()));
        return toResponse(productRepository.saveAndFlush(product));
    }

    /** Only touches a {@code @NotAudited} field: an UPDATE is executed, but no revision is written. */
    public void markViewed(Long id) {
        find(id).markViewed();
    }

    /** Copies the values of an old revision onto the current product; history itself is never rewritten. */
    public ProductResponse revert(Long id, Long revision) {
        Product product = find(id);
        Product old = productRepository.findRevision(id, revision)
                .orElseThrow(() -> new ResourceNotFoundException("Product " + id + " has no revision " + revision))
                .getEntity();
        product.update(old.getName(), old.getPrice(), old.getStock(), category(old.getCategory().getId()));
        return toResponse(productRepository.saveAndFlush(product));
    }

    public void delete(Long id) {
        productRepository.delete(find(id));
    }

    Product find(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product " + id + " not found"));
    }

    Category category(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category " + id + " not found"));
    }

    static ProductResponse toResponse(Product p) {
        return new ProductResponse(p.getId(), p.getName(), p.getPrice(), p.getStock(), p.getCategory().getName(),
                p.getCreatedAt(), p.getCreatedBy(), p.getLastModifiedAt(), p.getLastModifiedBy());
    }
}
