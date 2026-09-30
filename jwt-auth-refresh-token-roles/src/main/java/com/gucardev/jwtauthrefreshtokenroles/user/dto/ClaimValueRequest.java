package com.gucardev.jwtauthrefreshtokenroles.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ClaimValueRequest(@NotBlank @Size(max = 255) String value) {
}
