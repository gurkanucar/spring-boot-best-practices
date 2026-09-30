package com.gucardev.jwtauthrefreshtokenroles.user.dto;

import jakarta.validation.constraints.NotBlank;

public record AdminResetPasswordRequest(@NotBlank String newPassword) {
}
