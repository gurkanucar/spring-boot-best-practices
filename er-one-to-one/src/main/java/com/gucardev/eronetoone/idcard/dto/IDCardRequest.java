package com.gucardev.eronetoone.idcard.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record IDCardRequest(

        @NotBlank(message = "must not be blank")
        @Size(max = 20, message = "must be at most 20 characters")
        String cardNumber,

        @NotNull(message = "must not be null")
        @Future(message = "must be a future date")
        LocalDate expiryDate) {
}
