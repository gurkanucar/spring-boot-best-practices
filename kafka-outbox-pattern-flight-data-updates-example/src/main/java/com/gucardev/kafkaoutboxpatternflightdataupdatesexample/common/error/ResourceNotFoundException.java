package com.gucardev.kafkaoutboxpatternflightdataupdatesexample.common.error;

public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
