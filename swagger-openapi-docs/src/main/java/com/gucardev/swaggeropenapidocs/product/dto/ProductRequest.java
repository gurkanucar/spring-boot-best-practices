package com.gucardev.swaggeropenapidocs.product.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

@Schema(
        name = "ProductRequest",
        description = "Payload for creating a product. The examples on each field are what "
                + "Swagger UI pre-fills into Try it out.",
        example = """
                {
                  "sku": "SKU-0042",
                  "name": "Mechanical Keyboard",
                  "price": 1499.90,
                  "stock": 12
                }""")
public record ProductRequest(
        @Schema(
                        description = "Stock keeping unit. Unique across products; a repeat returns 409.",
                        example = "SKU-0042",
                        maxLength = 64,
                        requiredMode = Schema.RequiredMode.REQUIRED)
                @NotBlank
                @Size(max = 64)
                String sku,
        @Schema(
                        description = "Display name.",
                        example = "Mechanical Keyboard",
                        maxLength = 200,
                        requiredMode = Schema.RequiredMode.REQUIRED)
                @NotBlank
                @Size(max = 200)
                String name,
        @Schema(
                        description = "Unit price. Held as BigDecimal; never a floating-point type.",
                        example = "1499.90",
                        minimum = "0",
                        exclusiveMinimum = true,
                        requiredMode = Schema.RequiredMode.REQUIRED)
                @Positive
                BigDecimal price,
        @Schema(
                        description = "Units on hand. Zero is allowed.",
                        example = "12",
                        minimum = "0",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                @PositiveOrZero
                Integer stock) {}
