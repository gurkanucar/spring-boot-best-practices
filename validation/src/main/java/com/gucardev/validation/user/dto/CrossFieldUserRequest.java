package com.gucardev.validation.user.dto;

import com.gucardev.validation.constraint.PasswordsMatch;
import com.gucardev.validation.constraint.StrongPassword;
import com.gucardev.validation.constraint.ValidDateRange;
import java.time.LocalDate;

/**
 * Combines two class-level constraints on one DTO. Presence checks are
 * deliberately omitted; this DTO demonstrates cross-field behavior only.
 */
@PasswordsMatch
@ValidDateRange
public record CrossFieldUserRequest(

        @StrongPassword
        String password,

        String passwordConfirm,

        LocalDate startDate,

        LocalDate endDate) {
}
