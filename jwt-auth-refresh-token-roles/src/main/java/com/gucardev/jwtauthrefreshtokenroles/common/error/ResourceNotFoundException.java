package com.gucardev.jwtauthrefreshtokenroles.common.error;

/** 404. */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
