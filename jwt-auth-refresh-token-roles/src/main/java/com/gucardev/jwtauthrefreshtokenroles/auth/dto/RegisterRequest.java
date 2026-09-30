package com.gucardev.jwtauthrefreshtokenroles.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** The password is checked by PasswordPolicy in the service, so the rule lives in one place. */
public record RegisterRequest(
        @NotBlank @Email @Size(max = 320) String email,
        @NotBlank String password,
        @Pattern(regexp = "^\\+[1-9]\\d{7,14}$", message = "must be in E.164 format, e.g. +905551112233")
        String phone) {
}
