package com.gucardev.swaggeropenapidocs.product.controller;

import com.gucardev.swaggeropenapidocs.api.ApiResult;
import com.gucardev.swaggeropenapidocs.api.ApiResultFactory;
import com.gucardev.swaggeropenapidocs.product.dto.ProductPageRequest;
import com.gucardev.swaggeropenapidocs.product.dto.ProductRequest;
import com.gucardev.swaggeropenapidocs.product.dto.ProductResponse;
import com.gucardev.swaggeropenapidocs.product.service.ProductService;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
@Slf4j
@Tag(
        name = "Products",
        description = "Product catalog: CRUD-style endpoints written to exercise every common "
                + "OpenAPI documentation technique - schemas, examples, parameters, and the "
                + "documented-status-code catalogue.")
public class ProductController {

    private final ProductService productService;
    private final ApiResultFactory results;

    @Operation(
            summary = "List products (paged)",
            description = "Pagination is a single @ParameterObject (ProductPageRequest) - "
                    + "springdoc flattens its fields into separate page/size query parameters "
                    + "instead of documenting one nested object parameter.")
    @ApiResponse(responseCode = "200", description = "A page of products")
    @GetMapping
    public ApiResult<List<ProductResponse>> list(
            @ParameterObject ProductPageRequest query,
            // @Parameter(hidden = true): still accepted on the wire, but intentionally left
            // out of the generated doc - internal service-to-service callers use it, public
            // API consumers are not meant to know it exists.
            @Parameter(hidden = true) @RequestHeader(value = "X-Internal-Client", required = false)
                    String internalClient) {
        if (internalClient != null) {
            log.debug("Internal client call from {}", internalClient);
        }
        int page = query.pageOrDefault();
        int size = query.sizeOrDefault();
        var slice = productService.findPage(page, size);
        return results.page(slice.content(), page, size, slice.totalElements());
    }

    @Operation(
            summary = "List all products, slowly",
            description = "Sleeps for a random 200-800 ms before responding. Useful for trying "
                    + "out Swagger UI's \"display request duration\" setting.")
    @ApiResponse(responseCode = "200", description = "All products, after a simulated delay")
    @GetMapping("/slow")
    public ApiResult<List<ProductResponse>> slow() {
        return results.ok(productService.findAllSlowly());
    }

    @Operation(
            summary = "Fail on purpose",
            description = "Always throws, so the documented 500 response has real behavior "
                    + "behind it instead of being declared with nothing to back it up.")
    @ApiResponse(responseCode = "500", description = "Always; this is the point of the endpoint")
    @GetMapping("/exception")
    public void simulateException() {
        productService.simulateException();
    }

    @Operation(
            summary = "Get one product by id",
            description = "A missing id returns 404 through the ApiResult envelope. A "
                    + "non-numeric id is a 400 from Spring's path-variable conversion.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "The product"),
        @ApiResponse(responseCode = "400", description = "Id is not a number"),
        @ApiResponse(responseCode = "404", description = "No product with that id")
    })
    @GetMapping("/{id}")
    public ApiResult<ProductResponse> get(
            @Parameter(description = "Product id as returned by the list endpoint", example = "1")
                    @PathVariable
                    Long id) {
        return results.ok(productService.findById(id));
    }

    @Operation(
            summary = "Create a product",
            description = "Request body documented with two named examples - a valid payload "
                    + "and one that fails validation - both replayed against this exact endpoint "
                    + "by OpenApiDocsTest.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Created"),
        @ApiResponse(responseCode = "400", description = "Validation failed"),
        @ApiResponse(responseCode = "409", description = "That SKU already exists")
    })
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            content =
                    @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ProductRequest.class),
                            examples = {
                                @ExampleObject(
                                        name = "valid",
                                        summary = "A product that will be created",
                                        value =
                                                """
                                                {
                                                  "sku": "SKU-0042",
                                                  "name": "Mechanical Keyboard",
                                                  "price": 1499.90,
                                                  "stock": 12
                                                }"""),
                                @ExampleObject(
                                        name = "invalid",
                                        summary = "Fails validation, returns 400",
                                        value =
                                                """
                                                {
                                                  "sku": "",
                                                  "name": "",
                                                  "price": -5,
                                                  "stock": -1
                                                }""")
                            }))
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResult<ProductResponse> create(@Valid @RequestBody ProductRequest request) {
        return results.ok(productService.create(request), "Product created");
    }

    // @Hidden removes the whole operation from /v3/api-docs and Swagger UI - unlike the
    // profile-level springdoc.api-docs.enabled=false in application-prod.yaml, this is
    // per-endpoint: the route stays live and callable, only the documentation disappears.
    @Hidden
    @Operation(
            summary = "Reset the in-memory catalog",
            description = "Test/demo utility, not part of the public API surface.")
    @PostMapping("/internal/reset")
    public ApiResult<Void> reset() {
        productService.reset();
        return results.ok(null, "Catalog reset");
    }
}
