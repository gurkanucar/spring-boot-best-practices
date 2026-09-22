package com.gucardev.swaggeropenapidocs.exception;

import com.gucardev.swaggeropenapidocs.api.ApiResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

// Every response, success or failure, goes out as an ApiResult - this is the one place
// that turns an exception into that shape.
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiResult<Void>> handleApiException(ApiException exception) {
        log.info("{} -> {}", exception.getErrorCode(), exception.getStatus());
        return ResponseEntity.status(exception.getStatus())
                .body(ApiResult.error(exception.getErrorCode(), exception.getMessage(), null));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResult<Void>> handleValidation(MethodArgumentNotValidException exception) {
        var errors = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> new ApiResult.FieldError(error.getField(), error.getDefaultMessage()))
                .toList();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResult.error("VALIDATION_FAILED", "Request validation failed", errors));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResult<Void>> handleUnexpected(Exception exception) throws Exception {
        // Exceptions that already know their own status (e.g. NoResourceFoundException for an
        // unmapped/disabled route) are left to Spring's own resolvers - only genuinely
        // unhandled ones become a 500.
        if (exception instanceof ErrorResponse) {
            throw exception;
        }
        log.error("Unhandled exception", exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResult.error("INTERNAL_ERROR", "Unexpected error", null));
    }
}
