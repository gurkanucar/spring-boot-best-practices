package com.gucardev.jwtauthrefreshtokenroles.auth.dto;

import com.gucardev.jwtauthrefreshtokenroles.otp.service.OtpChannel;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ForgotPasswordRequest(@NotBlank @Email String email, @NotNull OtpChannel channel) {
}
