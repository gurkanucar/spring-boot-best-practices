package com.gucardev.openobserve.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(Exception.class)
    public ProblemDetail handle(Exception exception) throws Exception {
        // Spring's own 4xx exceptions keep their default handling.
        if (exception instanceof ErrorResponse) {
            throw exception;
        }
        // The "unhandled_exception_logged" real-time alert matches on this exact text.
        log.error("Unhandled exception", exception);
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        problem.setDetail("Unexpected error");
        return problem;
    }
}
