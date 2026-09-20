package com.gucardev.restapidesign.error;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/** Single exit point for validation and business-rule error responses. */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private final ProblemDetailFactory problems;

    public GlobalExceptionHandler(ProblemDetailFactory problems) {
        this.problems = problems;
    }

    /** @Valid @RequestBody violation: field-level errors from Bean Validation. */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {

        List<ApiErrorDetail> errors = new ArrayList<>();
        ex.getBindingResult().getFieldErrors().forEach(error -> errors.add(ApiErrorDetail.of(error)));

        ProblemDetail problem = problems.of(HttpStatus.BAD_REQUEST, "Validation Failed",
                "The request did not pass validation", errors, instanceOf(request));
        return ResponseEntity.badRequest().body(problem);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleNotFound(ResourceNotFoundException ex, HttpServletRequest request) {
        ProblemDetail problem = problems.of(HttpStatus.NOT_FOUND, "Not Found", ex.getMessage(), List.of(), instanceOf(request));
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem);
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ProblemDetail> handleConflict(ConflictException ex, HttpServletRequest request) {
        ProblemDetail problem = problems.of(HttpStatus.CONFLICT, "Conflict", ex.getMessage(), List.of(), instanceOf(request));
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
    }

    @ExceptionHandler(PreconditionRequiredException.class)
    public ResponseEntity<ProblemDetail> handlePreconditionRequired(PreconditionRequiredException ex, HttpServletRequest request) {
        ProblemDetail problem = problems.of(HttpStatus.PRECONDITION_REQUIRED, "Precondition Required", ex.getMessage(), List.of(), instanceOf(request));
        return ResponseEntity.status(HttpStatus.PRECONDITION_REQUIRED).body(problem);
    }

    @ExceptionHandler(PreconditionFailedException.class)
    public ResponseEntity<ProblemDetail> handlePreconditionFailed(PreconditionFailedException ex, HttpServletRequest request) {
        ProblemDetail problem = problems.of(HttpStatus.PRECONDITION_FAILED, "Precondition Failed", ex.getMessage(), List.of(), instanceOf(request));
        return ResponseEntity.status(HttpStatus.PRECONDITION_FAILED).body(problem);
    }

    /** Malformed query params (e.g. an unknown ?sort= field) — request never reached business logic. */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ProblemDetail> handleBadRequest(IllegalArgumentException ex, HttpServletRequest request) {
        ProblemDetail problem = problems.of(HttpStatus.BAD_REQUEST, "Bad Request", ex.getMessage(), List.of(), instanceOf(request));
        return ResponseEntity.badRequest().body(problem);
    }

    static URI instanceOf(WebRequest request) {
        return URI.create(((ServletWebRequest) request).getRequest().getRequestURI());
    }

    static URI instanceOf(HttpServletRequest request) {
        return URI.create(request.getRequestURI());
    }
}
