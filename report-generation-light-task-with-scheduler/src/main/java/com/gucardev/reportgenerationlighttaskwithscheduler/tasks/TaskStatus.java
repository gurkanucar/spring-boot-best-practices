package com.gucardev.reportgenerationlighttaskwithscheduler.tasks;

public enum TaskStatus {

    /** Waiting for {@code run_at}; also the state between retries. */
    PENDING,

    /** Claimed by an instance ({@code locked_by}) and executing. */
    RUNNING,

    SUCCEEDED,

    /** Reserved in the schema. The runner reschedules failures (PENDING) or gives up (DEAD). */
    FAILED,

    /** Gave up: a non-retryable error or the last attempt failed. Needs a person to look at it. */
    DEAD
}
