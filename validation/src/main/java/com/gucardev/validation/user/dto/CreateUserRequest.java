package com.gucardev.validation.user.dto;

import com.gucardev.validation.constraint.EnumValue;
import com.gucardev.validation.constraint.TcKimlikNo;
import com.gucardev.validation.constraint.UniqueEmail;
import com.gucardev.validation.user.Role;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Real create flow: DTO, service and database constraints all stack up. */
public record CreateUserRequest(

        @NotBlank(message = "{validation.user.fullName.notblank}")
        @Size(min = 3, max = 100, message = "{validation.user.fullName.size}")
        String fullName,

        @NotBlank(message = "{validation.user.email.notblank}")
        @Email(message = "{validation.user.email.invalid}")
        @UniqueEmail
        String email,

        @NotBlank(message = "{validation.user.tckn.invalid}")
        @TcKimlikNo
        String tcKimlikNo,

        @Min(value = 18, message = "{validation.user.age.min}")
        int age,

        @NotBlank(message = "{validation.user.role.enum}")
        @EnumValue(enumClass = Role.class, message = "{validation.user.role.enum}")
        String role,

        @NotNull(message = "{validation.address.notnull}")
        @Valid
        AddressRequest address) {
}
