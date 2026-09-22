package com.gucardev.swaggeropenapidocs.product.dto;

import io.swagger.v3.oas.annotations.media.Schema;

// Bound with @ParameterObject on the controller method: springdoc flattens page/size into
// two separate query parameters instead of documenting this as a single object parameter.
@Schema(name = "ProductPageRequest", description = "Pagination request, flattened into query parameters.")
public record ProductPageRequest(
        @Schema(description = "Zero-based page index.", example = "0", defaultValue = "0") Integer page,
        @Schema(description = "Page size.", example = "20", defaultValue = "20") Integer size) {

    public int pageOrDefault() {
        return page == null ? 0 : page;
    }

    public int sizeOrDefault() {
        return size == null ? 20 : size;
    }
}
