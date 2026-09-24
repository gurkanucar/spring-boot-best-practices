package com.gucardev.reportgenerationlighttaskwithscheduler.tasks;

/** Retrying will not help (bad payload, missing data...): the task goes straight to DEAD. */
public class NonRetryableTaskException extends RuntimeException {

    public NonRetryableTaskException(String message) {
        super(message);
    }

    public NonRetryableTaskException(String message, Throwable cause) {
        super(message, cause);
    }
}
