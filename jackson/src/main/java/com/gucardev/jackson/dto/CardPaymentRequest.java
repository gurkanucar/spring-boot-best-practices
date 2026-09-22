package com.gucardev.jackson.dto;

import com.gucardev.jackson.convert.CardNumberDeserializer;
import com.gucardev.jackson.convert.CardNumberSerializer;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.annotation.JsonSerialize;

public record CardPaymentRequest(
        @NotBlank
                @JsonSerialize(using = CardNumberSerializer.class)
                @JsonDeserialize(using = CardNumberDeserializer.class)
                String cardNumber,
        // BigDecimal, not double: exact decimal arithmetic, and Jackson renders it as a
        // plain "100.50" - never scientific notation or a float-rounded value.
        @Positive BigDecimal amount) {}
