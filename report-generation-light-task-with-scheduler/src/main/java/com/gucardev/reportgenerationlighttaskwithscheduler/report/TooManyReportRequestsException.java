package com.gucardev.reportgenerationlighttaskwithscheduler.report;

import java.time.Duration;
import lombok.Getter;

@Getter
public class TooManyReportRequestsException extends RuntimeException {

    private final Duration retryAfter;

    public TooManyReportRequestsException(int maxRequests, Duration window, Duration retryAfter) {
        super("At most %d reports per %d minutes. Try again in %d seconds."
                .formatted(maxRequests, window.toMinutes(), retryAfter.toSeconds()));
        this.retryAfter = retryAfter;
    }
}
