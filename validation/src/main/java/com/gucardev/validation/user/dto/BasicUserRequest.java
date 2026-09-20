package com.gucardev.validation.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * Jakarta Bean Validation's built-in annotations; each field demonstrates a different constraint kind.
 */
public record BasicUserRequest(

        @NotBlank(message = "{validation.user.fullName.notblank}")
        @Size(min = 3, max = 100, message = "{validation.user.fullName.size}")
        String fullName,

        @NotBlank(message = "{validation.user.email.notblank}")
        @Email(message = "{validation.user.email.invalid}")
        String email,

        @Min(value = 18, message = "{validation.user.age.min}")
        int age,

        @NotNull(message = "{validation.user.birthDate.notnull}")
        @Past(message = "{validation.user.birthDate.past}")
        LocalDate birthDate,

        @Pattern(regexp = "^05\\d{9}$", message = "{validation.user.phone.pattern}")
        String phone) {
}
