package com.gucardev.restapiintegration.product;

import com.gucardev.restapiintegration.product.dto.ProductDto;
import com.gucardev.restapiintegration.product.dto.ProductPatchRequest;
import com.gucardev.restapiintegration.product.dto.ProductRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** This application's own API. Every endpoint is answered by calling the remote product API. */
@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductClient client;
    private final ProductHttpApi httpApi;

    public ProductController(ProductClient client, ProductHttpApi httpApi) {
        this.client = client;
        this.httpApi = httpApi;
    }

    @GetMapping
    public List<ProductDto> list(@RequestParam(required = false) String name) {
        return client.list(name);
    }

    @GetMapping("/{id}")
    public ProductDto get(@PathVariable long id) {
        return client.get(id);
    }

    /** Same call through the declarative HTTP interface instead of the fluent client. */
    @GetMapping("/{id}/via-http-interface")
    public ProductDto getViaHttpInterface(@PathVariable long id) {
        return httpApi.get(id);
    }

    /**
     * The idempotency key is created once here, outside the retried method, so every retry of
     * the POST carries the same key and the remote side creates the product only once.
     * A caller may send its own key to make its own retries safe as well.
     */
    @PostMapping
    public ResponseEntity<ProductDto> create(@Valid @RequestBody ProductRequest request,
                                             @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey) {
        String key = idempotencyKey != null ? idempotencyKey : UUID.randomUUID().toString();
        ProductClient.CreateResult result = client.create(request, key);
        if (!result.created()) {
            return ResponseEntity.ok(result.product());
        }
        return ResponseEntity.created(URI.create("/api/products/" + result.product().id())).body(result.product());
    }

    @PutMapping("/{id}")
    public ProductDto replace(@PathVariable long id, @Valid @RequestBody ProductRequest request) {
        return client.replace(id, request);
    }

    @PatchMapping("/{id}")
    public ProductDto patch(@PathVariable long id, @Valid @RequestBody ProductPatchRequest request) {
        return client.patch(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id) {
        client.delete(id);
    }
}
