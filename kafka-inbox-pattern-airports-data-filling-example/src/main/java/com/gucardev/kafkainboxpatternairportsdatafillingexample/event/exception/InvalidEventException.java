package com.gucardev.kafkainboxpatternairportsdatafillingexample.event.exception;

public class InvalidEventException extends RuntimeException {

    public InvalidEventException(String message) {
        super(message);
    }
}
