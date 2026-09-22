package com.gucardev.swaggeropenapidocs.api;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "PageInfo", description = "Pagination metadata alongside the data array.")
public record PageInfo(
        @Schema(description = "Zero-based page index.", example = "0") int page,
        @Schema(description = "Requested page size.", example = "20") int size,
        @Schema(description = "Total number of elements across all pages.", example = "3") long totalElements,
        @Schema(description = "Total number of pages.", example = "1") int totalPages) {

    public static PageInfo of(int page, int size, long totalElements) {
        int totalPages = size == 0 ? 0 : (int) Math.ceil((double) totalElements / size);
        return new PageInfo(page, size, totalElements, totalPages);
    }
}
