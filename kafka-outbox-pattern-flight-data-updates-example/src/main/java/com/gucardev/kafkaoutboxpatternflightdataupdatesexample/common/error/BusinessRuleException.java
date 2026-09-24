package com.gucardev.kafkaoutboxpatternflightdataupdatesexample.common.error;

/** A well-formed request that breaks a flight rule, e.g. an arrival before the departure. */
public class BusinessRuleException extends RuntimeException {

    public BusinessRuleException(String message) {
        super(message);
    }
}
