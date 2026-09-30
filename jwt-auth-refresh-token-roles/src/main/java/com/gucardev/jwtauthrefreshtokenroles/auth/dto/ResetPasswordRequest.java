package com.gucardev.jwtauthrefreshtokenroles.auth.dto;

import jakarta.validation.constraints.NotBlank;

/** Either {token} from the mail link, or {email, code} from the SMS — never both. */
public record ResetPasswordRequest(String token, String email, String code, @NotBlank String newPassword) {
}
