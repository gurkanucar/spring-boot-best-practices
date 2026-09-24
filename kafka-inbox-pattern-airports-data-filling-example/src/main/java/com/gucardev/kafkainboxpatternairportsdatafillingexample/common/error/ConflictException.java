package com.gucardev.kafkainboxpatternairportsdatafillingexample.common.error;

public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
