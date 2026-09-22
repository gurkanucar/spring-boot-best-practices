package com.gucardev.swaggeropenapidocs.product.service;

import com.gucardev.swaggeropenapidocs.exception.ApiException;
import com.gucardev.swaggeropenapidocs.product.dto.ProductRequest;
import com.gucardev.swaggeropenapidocs.product.dto.ProductResponse;
import com.gucardev.swaggeropenapidocs.product.model.Product;
import com.gucardev.swaggeropenapidocs.product.repository.ProductRepository;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductService {

    private final ProductRepository productRepository;

    public record Slice<T>(List<T> content, long totalElements) {}

    public Slice<ProductResponse> findPage(int page, int size) {
        List<Product> all = productRepository.findAll();
        int fromIndex = Math.min(page * size, all.size());
        int toIndex = Math.min(fromIndex + size, all.size());
        List<ProductResponse> content =
                all.subList(fromIndex, toIndex).stream().map(ProductResponse::from).toList();
        return new Slice<>(content, all.size());
    }

    public ProductResponse findById(Long id) {
        return productRepository
                .findById(id)
                .map(ProductResponse::from)
                .orElseThrow(() ->
                        new ApiException("PRD-1001", HttpStatus.NOT_FOUND, "No product with id " + id));
    }

    public ProductResponse create(ProductRequest request) {
        productRepository.findBySku(request.sku()).ifPresent(existing -> {
            throw new ApiException(
                    "PRD-1002", HttpStatus.CONFLICT, "SKU already exists: " + request.sku());
        });
        Product saved = productRepository.save(Product.builder()
                .sku(request.sku())
                .name(request.name())
                .price(request.price())
                .stock(request.stock())
                .build());
        log.info("Created product id={} sku={}", saved.getId(), saved.getSku());
        return ProductResponse.from(saved);
    }

    public List<ProductResponse> findAllSlowly() {
        long delayMillis = ThreadLocalRandom.current().nextLong(200L, 800L);
        log.info("Simulating work delayMillis={}", delayMillis);
        try {
            Thread.sleep(delayMillis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while simulating work", e);
        }
        return productRepository.findAll().stream().map(ProductResponse::from).toList();
    }

    public void simulateException() {
        log.warn("About to fail on purpose");
        throw new IllegalStateException("Deliberate failure to show a documented 500 response");
    }

    public void reset() {
        log.info("Resetting catalog to its seed data");
        productRepository.reset();
    }
}
