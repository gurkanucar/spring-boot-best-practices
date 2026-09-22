package com.gucardev.swaggeropenapidocs.product.repository;

import com.gucardev.swaggeropenapidocs.product.model.Product;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Repository;

// In-memory, seeded on startup - just enough persistence to make the documented endpoints
// behave realistically without pulling in a database.
@Repository
public class ProductRepository {

    private final ConcurrentHashMap<Long, Product> products = new ConcurrentHashMap<>();
    private final AtomicLong sequence = new AtomicLong();

    public ProductRepository() {
        seed();
    }

    public void reset() {
        products.clear();
        sequence.set(0);
        seed();
    }

    private void seed() {
        save(Product.builder()
                .sku("SKU-0001")
                .name("Mechanical Keyboard")
                .price(new BigDecimal("1499.90"))
                .stock(12)
                .build());
        save(Product.builder()
                .sku("SKU-0002")
                .name("Wireless Mouse")
                .price(new BigDecimal("349.50"))
                .stock(40)
                .build());
        save(Product.builder()
                .sku("SKU-0003")
                .name("USB-C Hub")
                .price(new BigDecimal("599.00"))
                .stock(0)
                .build());
    }

    public List<Product> findAll() {
        return products.values().stream().sorted(Comparator.comparing(Product::getId)).toList();
    }

    public Optional<Product> findById(Long id) {
        return Optional.ofNullable(products.get(id));
    }

    public Optional<Product> findBySku(String sku) {
        return products.values().stream().filter(p -> p.getSku().equals(sku)).findFirst();
    }

    public Product save(Product product) {
        if (product.getId() == null) {
            product.setId(sequence.incrementAndGet());
        }
        products.put(product.getId(), product);
        return product;
    }
}
