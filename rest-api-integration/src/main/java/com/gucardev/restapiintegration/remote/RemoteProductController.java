package com.gucardev.restapiintegration.remote;

import java.math.BigDecimal;
import java.net.URI;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The SIMULATED third-party product API. It only exists so the client examples have something
 * real to talk to over HTTP; in a real project this would be someone else's service.
 * Protected by {@link RemoteAuthFilter}.
 */
@RestController
@RequestMapping("/remote-api/products")
class RemoteProductController {

    record RemoteProduct(Long id, String name, BigDecimal price) {
    }

    record ProductBody(String name, BigDecimal price) {
    }

    private final Map<Long, RemoteProduct> products = new ConcurrentHashMap<>();
    private final Map<String, Long> idempotencyKeys = new ConcurrentHashMap<>();
    private final AtomicLong ids = new AtomicLong();

    RemoteProductController() {
        save("Keyboard", new BigDecimal("49.90"));
        save("Mouse", new BigDecimal("19.90"));
    }

    @GetMapping
    List<RemoteProduct> list(@RequestParam(required = false) String name) {
        return products.values().stream()
                .filter(p -> name == null || p.name().toLowerCase(Locale.ROOT).contains(name.toLowerCase(Locale.ROOT)))
                .sorted(Comparator.comparing(RemoteProduct::id))
                .toList();
    }

    @GetMapping("/{id}")
    ResponseEntity<RemoteProduct> get(@PathVariable Long id) {
        return ResponseEntity.of(Optional.ofNullable(products.get(id)));
    }

    /** Honours an {@code Idempotency-Key}: repeating the same POST returns the same product. */
    @PostMapping
    ResponseEntity<?> create(@RequestBody ProductBody body,
                             @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey) {
        ResponseEntity<?> invalid = validate(body);
        if (invalid != null) {
            return invalid;
        }
        if (idempotencyKey != null && idempotencyKeys.containsKey(idempotencyKey)) {
            return ResponseEntity.ok(products.get(idempotencyKeys.get(idempotencyKey)));
        }
        RemoteProduct product = save(body.name(), body.price());
        if (idempotencyKey != null) {
            idempotencyKeys.put(idempotencyKey, product.id());
        }
        return ResponseEntity.created(URI.create("/remote-api/products/" + product.id())).body(product);
    }

    @PutMapping("/{id}")
    ResponseEntity<?> replace(@PathVariable Long id, @RequestBody ProductBody body) {
        ResponseEntity<?> invalid = validate(body);
        if (invalid != null) {
            return invalid;
        }
        if (!products.containsKey(id)) {
            return ResponseEntity.notFound().build();
        }
        RemoteProduct updated = new RemoteProduct(id, body.name(), body.price());
        products.put(id, updated);
        return ResponseEntity.ok(updated);
    }

    /** Partial update: a missing or null field keeps its current value. */
    @PatchMapping("/{id}")
    ResponseEntity<RemoteProduct> patch(@PathVariable Long id, @RequestBody ProductBody body) {
        RemoteProduct current = products.get(id);
        if (current == null) {
            return ResponseEntity.notFound().build();
        }
        RemoteProduct patched = new RemoteProduct(id,
                body.name() != null ? body.name() : current.name(),
                body.price() != null ? body.price() : current.price());
        products.put(id, patched);
        return ResponseEntity.ok(patched);
    }

    @DeleteMapping("/{id}")
    ResponseEntity<Void> delete(@PathVariable Long id) {
        return products.remove(id) != null ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }

    private RemoteProduct save(String name, BigDecimal price) {
        RemoteProduct product = new RemoteProduct(ids.incrementAndGet(), name, price);
        products.put(product.id(), product);
        return product;
    }

    private static ResponseEntity<?> validate(ProductBody body) {
        if (body.name() == null || body.name().isBlank() || body.price() == null || body.price().signum() < 0) {
            return ResponseEntity.badRequest().body(
                    ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                            "name is required and price must be zero or positive"));
        }
        return null;
    }
}
