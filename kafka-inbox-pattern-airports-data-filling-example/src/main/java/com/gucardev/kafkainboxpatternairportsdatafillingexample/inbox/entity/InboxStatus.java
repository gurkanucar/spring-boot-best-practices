package com.gucardev.kafkainboxpatternairportsdatafillingexample.inbox.entity;

public enum InboxStatus {
    /** Stored, waiting to be applied (also: waiting for a retry after a failure). */
    PENDING,
    /** Applied to the airport table. */
    PROCESSED,
    /** Not applied because the airport already has this or a newer version. */
    SKIPPED,
    /** Gave up: invalid payload, or still failing after max-attempts. Needs a human. */
    FAILED
}
