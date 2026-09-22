package com.gucardev.swaggeropenapidocs.product.model;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// A plain in-memory model - no JPA/DB here, since the point of this module is the OpenAPI
// documentation layer, not persistence.
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Product {

    private Long id;
    private String sku;
    private String name;
    private BigDecimal price;
    private Integer stock;
}
