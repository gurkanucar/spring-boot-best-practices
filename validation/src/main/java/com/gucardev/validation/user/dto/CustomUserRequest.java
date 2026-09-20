package com.gucardev.validation.user.dto;

import com.gucardev.validation.constraint.EnumValue;
import com.gucardev.validation.constraint.StrongPassword;
import com.gucardev.validation.constraint.TcKimlikNo;
import com.gucardev.validation.user.Role;
import jakarta.validation.constraints.NotBlank;

/** Combines the three custom field-level constraints on one DTO. */
public record CustomUserRequest(

        @NotBlank(message = "{validation.user.tckn.invalid}")
        @TcKimlikNo
        String tcKimlikNo,

        @NotBlank(message = "{validation.user.password.weak}")
        @StrongPassword
        String password,

        @NotBlank(message = "{validation.user.role.enum}")
        @EnumValue(enumClass = Role.class, message = "{validation.user.role.enum}")
        String role) {
}
