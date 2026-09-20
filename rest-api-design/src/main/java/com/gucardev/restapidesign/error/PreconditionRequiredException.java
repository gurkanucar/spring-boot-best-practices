package com.gucardev.restapidesign.error;

/** Signals a 428: a mutating request arrived without the required If-Match header. */
public class PreconditionRequiredException extends RuntimeException {

    public PreconditionRequiredException(String message) {
        super(message);
    }
}
