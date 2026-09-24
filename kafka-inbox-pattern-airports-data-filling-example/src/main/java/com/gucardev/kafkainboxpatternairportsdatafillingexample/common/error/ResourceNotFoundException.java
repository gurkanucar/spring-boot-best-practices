package com.gucardev.kafkainboxpatternairportsdatafillingexample.common.error;

public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
