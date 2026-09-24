package com.gucardev.restapiintegration.product;

import com.gucardev.restapiintegration.client.error.RemoteApiServerException;
import com.gucardev.restapiintegration.product.dto.ProductDto;
import com.gucardev.restapiintegration.product.dto.ProductPatchRequest;
import com.gucardev.restapiintegration.product.dto.ProductRequest;
import java.net.URI;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * Typed client for the remote product API, written with the fluent {@link RestClient} API.
 * Base URL, auth, headers, timeouts and error mapping come from the configured client, so each
 * method only describes its own request.
 *
 * <p>Only safe-to-repeat calls are retried: GETs, and the POST because it carries an
 * idempotency key that the caller creates once for all attempts.
 */
@Component
public class ProductClient {

    public record CreateResult(ProductDto product, boolean created, URI remoteLocation) {
    }

    private final RestClient restClient;

    public ProductClient(@Qualifier("remoteApi") RestClient restClient) {
        this.restClient = restClient;
    }

    /** GET with an optional query parameter and a generic list body. */
    @Retryable(includes = {RemoteApiServerException.class, ResourceAccessException.class}, maxRetries = 2, delay = 200)
    public List<ProductDto> list(String nameFilter) {
        return restClient.get()
                // "{name}" is a URI variable, so the value is fully encoded ("Fish & Chips" stays one value).
                .uri(uri -> nameFilter == null
                        ? uri.path("/products").build()
                        : uri.path("/products").queryParam("name", "{name}").build(nameFilter))
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });
    }

    /** GET by path variable. A 404 becomes a domain exception instead of the generic remote error. */
    @Retryable(includes = {RemoteApiServerException.class, ResourceAccessException.class}, maxRetries = 2, delay = 200)
    public ProductDto get(long id) {
        return restClient.get()
                .uri("/products/{id}", id)
                .retrieve()
                .onStatus(status -> status.value() == 404, (request, response) -> {
                    throw new ProductNotFoundException(id);
                })
                .body(ProductDto.class);
    }

    /**
     * POST with a JSON body and a custom header. {@code toEntity} gives access to the status and
     * headers too: 201 + Location for a new product, 200 when the idempotency key was seen before.
     */
    @Retryable(includes = {RemoteApiServerException.class, ResourceAccessException.class}, maxRetries = 2, delay = 200)
    public CreateResult create(ProductRequest request, String idempotencyKey) {
        ResponseEntity<ProductDto> response = restClient.post()
                .uri("/products")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", idempotencyKey)
                .body(request)
                .retrieve()
                .toEntity(ProductDto.class);
        return new CreateResult(response.getBody(),
                response.getStatusCode().isSameCodeAs(HttpStatus.CREATED),
                response.getHeaders().getLocation());
    }

    /** PUT: replaces the whole product. */
    public ProductDto replace(long id, ProductRequest request) {
        return restClient.put()
                .uri("/products/{id}", id)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .onStatus(status -> status.value() == 404, (req, res) -> {
                    throw new ProductNotFoundException(id);
                })
                .body(ProductDto.class);
    }

    /** PATCH: changes only the fields present in the request. */
    public ProductDto patch(long id, ProductPatchRequest request) {
        return restClient.patch()
                .uri("/products/{id}", id)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .onStatus(status -> status.value() == 404, (req, res) -> {
                    throw new ProductNotFoundException(id);
                })
                .body(ProductDto.class);
    }

    /** DELETE: no response body, so {@code toBodilessEntity()}. */
    public void delete(long id) {
        restClient.delete()
                .uri("/products/{id}", id)
                .retrieve()
                .onStatus(status -> status.value() == 404, (req, res) -> {
                    throw new ProductNotFoundException(id);
                })
                .toBodilessEntity();
    }
}
