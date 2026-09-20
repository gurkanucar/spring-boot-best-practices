package com.gucardev.validation.error;

import org.springframework.http.HttpStatus;

public class UserNotFoundException extends BusinessException {

    public UserNotFoundException(Long id) {
        super("User not found: " + id, HttpStatus.NOT_FOUND, "error.notfound.title",
                "error.user.notfound", String.valueOf(id));
    }
}
