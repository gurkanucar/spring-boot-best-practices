package com.gucardev.restapidesign.error;

/** Signals a 412: the If-Match header didn't match the resource's current ETag. */
public class PreconditionFailedException extends RuntimeException {

    public PreconditionFailedException(String message) {
        super(message);
    }
}
