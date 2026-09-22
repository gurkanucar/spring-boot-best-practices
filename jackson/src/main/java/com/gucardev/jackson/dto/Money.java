package com.gucardev.jackson.dto;

import java.math.BigDecimal;

// Serialized as a single compact string ("100.50 USD") by a module registered globally
// in JacksonModuleConfig - contrast with CardPaymentRequest.cardNumber, which uses
// @JsonSerialize/@JsonDeserialize locally on just that one field.
public record Money(BigDecimal amount, String currency) {}
