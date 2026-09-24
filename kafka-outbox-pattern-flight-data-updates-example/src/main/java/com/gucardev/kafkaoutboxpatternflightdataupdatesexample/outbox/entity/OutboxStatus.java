package com.gucardev.kafkaoutboxpatternflightdataupdatesexample.outbox.entity;

/** There is no FAILED state: an event that was committed must eventually be published. */
public enum OutboxStatus {

    PENDING,

    SENT
}
