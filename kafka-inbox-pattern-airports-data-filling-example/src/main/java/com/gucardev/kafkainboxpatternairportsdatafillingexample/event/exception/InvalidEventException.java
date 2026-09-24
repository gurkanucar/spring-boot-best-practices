package com.gucardev.kafkainboxpatternairportsdatafillingexample.event.exception;

/**
 * A Kafka message that can never be processed, however often it is retried (for example a
 * message key that does not match the airport code). The error handler sends it straight to the
 * dead letter topic.
 */
public class InvalidEventException extends RuntimeException {

    public InvalidEventException(String message) {
        super(message);
    }
}
