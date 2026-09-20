package com.gucardev.validation.user.dto;

import com.gucardev.validation.constraint.EnumValue;
import com.gucardev.validation.constraint.TcKimlikNo;
import com.gucardev.validation.user.Role;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Same as {@link CreateUserRequest} but skips both application-layer guards -
 *  no {@code @UniqueEmail}, no age {@code @Min} - so the database enforces both alone. */
public record UnsafeCreateUserRequest(

        @NotBlank(message = "{validation.user.fullName.notblank}")
        @Size(min = 3, max = 100, message = "{validation.user.fullName.size}")
        String fullName,

        @NotBlank(message = "{validation.user.email.notblank}")
        @Email(message = "{validation.user.email.invalid}")
        String email,

        @NotBlank(message = "{validation.user.tckn.invalid}")
        @TcKimlikNo
        String tcKimlikNo,

        int age,

        @NotBlank(message = "{validation.user.role.enum}")
        @EnumValue(enumClass = Role.class, message = "{validation.user.role.enum}")
        String role,

        @NotNull(message = "{validation.address.notnull}")
        @Valid
        AddressRequest address) {
}
