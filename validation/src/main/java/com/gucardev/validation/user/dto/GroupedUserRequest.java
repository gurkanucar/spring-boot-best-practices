package com.gucardev.validation.user.dto;

import com.gucardev.validation.constraint.StrongPassword;
import com.gucardev.validation.constraint.group.ValidationGroups.OnCreate;
import com.gucardev.validation.constraint.group.ValidationGroups.OnUpdate;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Null;

/**
 * Create: {@code id} must be absent, {@code password} required.
 * Update: {@code id} required, {@code password} optional but must be strong if present.
 */
public record GroupedUserRequest(

        @Null(groups = OnCreate.class, message = "{validation.user.id.null}")
        @NotNull(groups = OnUpdate.class, message = "{validation.user.id.notnull}")
        Long id,

        @NotBlank(groups = {OnCreate.class, OnUpdate.class}, message = "{validation.user.fullName.notblank}")
        String fullName,

        @NotBlank(groups = {OnCreate.class, OnUpdate.class}, message = "{validation.user.email.notblank}")
        @Email(groups = {OnCreate.class, OnUpdate.class}, message = "{validation.user.email.invalid}")
        String email,

        @NotBlank(groups = OnCreate.class, message = "{validation.user.password.weak}")
        @StrongPassword(groups = {OnCreate.class, OnUpdate.class})
        String password) {
}
