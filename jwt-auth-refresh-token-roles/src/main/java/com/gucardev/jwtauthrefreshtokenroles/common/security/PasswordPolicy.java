package com.gucardev.jwtauthrefreshtokenroles.common.security;

import com.gucardev.jwtauthrefreshtokenroles.common.error.BadRequestException;
import java.util.Optional;

/** Rules every new password must pass: registration, change, reset (JSON and form) and admin reset. */
public interface PasswordPolicy {

    /** Returns the violation message, or empty when the password is acceptable. */
    Optional<String> check(String password);

    default void validate(String password) {
        check(password).ifPresent(message -> {
            throw new BadRequestException(message);
        });
    }
}
