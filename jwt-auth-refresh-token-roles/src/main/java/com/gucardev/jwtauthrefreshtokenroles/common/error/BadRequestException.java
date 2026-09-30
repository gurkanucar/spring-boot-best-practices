package com.gucardev.jwtauthrefreshtokenroles.common.error;

/** 400: the request is well-formed but not acceptable (bad code, weak password, ...). */
public class BadRequestException extends RuntimeException {

    public BadRequestException(String message) {
        super(message);
    }
}
