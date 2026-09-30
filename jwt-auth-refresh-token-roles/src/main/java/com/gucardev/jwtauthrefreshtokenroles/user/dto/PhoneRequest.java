package com.gucardev.jwtauthrefreshtokenroles.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record PhoneRequest(
        @NotBlank
        @Pattern(regexp = "^\\+[1-9]\\d{7,14}$", message = "must be in E.164 format, e.g. +905551112233")
        String phone) {
}
