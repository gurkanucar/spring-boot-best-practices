package com.gucardev.swaggeropenapidocs.product.dto;

import com.gucardev.swaggeropenapidocs.product.model.Product;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

@Schema(
        name = "ProductResponse",
        description = "A product as returned by the API.",
        example = """
                {
                  "id": 1,
                  "sku": "SKU-0042",
                  "name": "Mechanical Keyboard",
                  "price": 1499.90,
                  "stock": 12
                }""")
public record ProductResponse(
        @Schema(description = "Identity, assigned on create.", example = "1") Long id,
        @Schema(description = "Stock keeping unit.", example = "SKU-0042") String sku,
        @Schema(description = "Display name.", example = "Mechanical Keyboard") String name,
        @Schema(description = "Unit price.", example = "1499.90") BigDecimal price,
        @Schema(description = "Units on hand.", example = "12") Integer stock) {

    public static ProductResponse from(Product product) {
        return new ProductResponse(
                product.getId(), product.getSku(), product.getName(), product.getPrice(), product.getStock());
    }
}
