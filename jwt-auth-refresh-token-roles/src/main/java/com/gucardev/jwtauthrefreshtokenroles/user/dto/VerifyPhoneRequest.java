package com.gucardev.jwtauthrefreshtokenroles.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record VerifyPhoneRequest(@NotBlank @Pattern(regexp = "\\d{6}") String code) {
}
