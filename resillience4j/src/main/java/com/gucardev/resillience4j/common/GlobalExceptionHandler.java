package com.gucardev.resillience4j.common;

import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import java.util.concurrent.TimeoutException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

/**
 * Turns resilience failures into honest HTTP answers. Note the split: when WE refuse to call
 * (open circuit, full bulkhead, no rate limit permit) it is 503 and the remote was not touched;
 * when the remote itself failed it is 502, or 504 when it was too slow.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(CallNotPermittedException.class)
    ResponseEntity<ProblemDetail> circuitOpen(CallNotPermittedException e) {
        return unavailable("Circuit breaker is open, the remote service was not called: " + e.getMessage(), 15);
    }

    @ExceptionHandler(BulkheadFullException.class)
    ResponseEntity<ProblemDetail> bulkheadFull(BulkheadFullException e) {
        return unavailable("Too many concurrent calls to the remote service: " + e.getMessage(), 1);
    }

    @ExceptionHandler(RequestNotPermitted.class)
    ResponseEntity<ProblemDetail> outboundRateLimited(RequestNotPermitted e) {
        return unavailable("No free slot to call the remote service in time: " + e.getMessage(), 1);
    }

    @ExceptionHandler(TimeoutException.class)
    ProblemDetail timeout(TimeoutException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.GATEWAY_TIMEOUT, "Remote service too slow: " + e.getMessage());
    }

    @ExceptionHandler({HttpServerErrorException.class, ResourceAccessException.class})
    ProblemDetail remoteFailed(Exception e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, "Remote service failed: " + e.getMessage());
    }

    @ExceptionHandler(HttpClientErrorException.class)
    ProblemDetail remoteRejected(HttpClientErrorException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY,
                "Remote service rejected the request (" + e.getStatusCode().value() + "), it was not retried");
    }

    private static ResponseEntity<ProblemDetail> unavailable(String detail, int retryAfterSeconds) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfterSeconds))
                .body(ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, detail));
    }
}
