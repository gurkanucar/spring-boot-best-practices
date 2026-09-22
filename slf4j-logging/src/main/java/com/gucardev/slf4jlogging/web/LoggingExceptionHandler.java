package com.gucardev.slf4jlogging.web;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class LoggingExceptionHandler {

    @ExceptionHandler(IllegalStateException.class)
    public ProblemDetail handleProcessingFailure(IllegalStateException ex) {
        // Throwable last: preserves type, message, cause and the complete stack trace.
        log.error("Order processing failed; see requestId for related events", ex);
        // Fluent equivalent: log.atError().setCause(ex).log("Order processing failed");
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
                "The demo operation failed. Use the requestId to find its logs.");
        problem.setProperty("requestId", MDC.get("requestId"));
        return problem;
    }
}
