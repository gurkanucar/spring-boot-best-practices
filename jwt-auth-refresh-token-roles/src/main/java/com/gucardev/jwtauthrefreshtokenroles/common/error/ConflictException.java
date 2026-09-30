package com.gucardev.jwtauthrefreshtokenroles.common.error;

/** 409: the request clashes with existing data (duplicate e-mail or phone). */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
