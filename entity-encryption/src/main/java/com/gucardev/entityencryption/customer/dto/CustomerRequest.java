package com.gucardev.entityencryption.customer.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** Used for both create and full update. */
public record CustomerRequest(
        @NotBlank String name,
        @NotBlank @Email String email,
        @NotBlank String nationalId,
        String phone) {
}
