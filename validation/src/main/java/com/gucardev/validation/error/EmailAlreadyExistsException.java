package com.gucardev.validation.error;

import org.springframework.http.HttpStatus;

public class EmailAlreadyExistsException extends BusinessException {

    public EmailAlreadyExistsException(String email) {
        super("Email address already registered: " + email, HttpStatus.CONFLICT, "error.conflict.title",
                "validation.user.email.unique");
    }
}
