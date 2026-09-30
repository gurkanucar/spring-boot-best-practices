package com.gucardev.jwtauthrefreshtokenroles.common.error;

/** 401: an unknown, expired or already used refresh token. */
public class InvalidTokenException extends RuntimeException {

    public InvalidTokenException(String message) {
        super(message);
    }
}
