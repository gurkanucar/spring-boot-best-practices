package com.gucardev.caching.examples.dto;

import java.math.BigDecimal;

public record OrderLine(String productId, int quantity, BigDecimal unitPrice) {
}
