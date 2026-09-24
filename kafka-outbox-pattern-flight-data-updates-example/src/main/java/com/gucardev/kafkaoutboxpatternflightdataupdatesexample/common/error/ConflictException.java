package com.gucardev.kafkaoutboxpatternflightdataupdatesexample.common.error;

/** The command does not fit the flight's current state, e.g. an arrival for a flight that never departed. */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
