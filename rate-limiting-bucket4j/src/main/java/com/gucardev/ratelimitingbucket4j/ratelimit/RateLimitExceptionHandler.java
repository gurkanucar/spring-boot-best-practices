package com.gucardev.ratelimitingbucket4j.ratelimit;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** The same 429 answer as {@link RateLimitFilter}, for limits charged from code with {@link RateLimiter}. */
@RestControllerAdvice
class RateLimitExceptionHandler {

    @ExceptionHandler(RateLimitExceededException.class)
    ResponseEntity<ProblemDetail> tooManyRequests(RateLimitExceededException e) {
        RateLimitBucket bucket = e.bucket();
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, e.getMessage());
        problem.setProperty("bucket", bucket.key());
        problem.setProperty("policy", bucket.policy());
        problem.setProperty("cost", e.cost());
        problem.setProperty("retryAfterSeconds", e.retryAfterSeconds());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(e.retryAfterSeconds()))
                .header("RateLimit-Limit", String.valueOf(bucket.smallestCapacity()))
                .header("RateLimit-Remaining", String.valueOf(e.decision().remaining()))
                .header("RateLimit-Policy", bucket.policy())
                .body(problem);
    }
}
