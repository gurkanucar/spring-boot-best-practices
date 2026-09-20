package com.gucardev.validation.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** Nested DTO; error paths look like {@code address.city}. */
public record AddressRequest(

        @NotBlank(message = "{validation.address.city.notblank}")
        String city,

        @NotBlank(message = "{validation.address.district.notblank}")
        String district,

        @Pattern(regexp = "^\\d{5}$", message = "{validation.address.postalCode.pattern}")
        String postalCode) {
}
