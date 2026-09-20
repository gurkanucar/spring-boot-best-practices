package com.gucardev.restapidesign.error;

/** Signals a 409: the request conflicts with the resource's current state. */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
