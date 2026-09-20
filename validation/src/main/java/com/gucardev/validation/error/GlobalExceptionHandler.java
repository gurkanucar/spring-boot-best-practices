package com.gucardev.validation.error;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Exit point for every validation error response; extends
 * {@link ResponseEntityExceptionHandler} for the body-validation and framework validation branches it overrides.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private final ProblemDetailFactory problems;

    public GlobalExceptionHandler(ProblemDetailFactory problems) {
        this.problems = problems;
    }

    /** {@code @Valid @RequestBody} violation - all field-level and class-level errors. */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {

        List<ValidationErrorDetail> errors = new ArrayList<>();
        ex.getBindingResult().getFieldErrors().forEach(error -> errors.add(ValidationErrorDetail.of(error)));
        ex.getBindingResult().getGlobalErrors().forEach(error -> errors.add(ValidationErrorDetail.of(error)));

        return ResponseEntity.badRequest().body(problems.validation(errors, instanceOf(request)));
    }

    /** {@code @PathVariable}/{@code @RequestParam} violation (Spring 6.1+); same response shape as body validation. */
    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException ex, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {

        List<ValidationErrorDetail> errors = new ArrayList<>();
        ex.getParameterValidationResults().forEach(result -> {
            String parameterName = result.getMethodParameter().getParameterName();
            String field = parameterName == null ? "parameter" : parameterName;
            result.getResolvableErrors().forEach(error ->
                    errors.add(ValidationErrorDetail.of(field, error, result.getArgument())));
        });

        return ResponseEntity.badRequest().body(problems.validation(errors, instanceOf(request)));
    }

    /** Malformed JSON or a type/enum conversion failure - request never reached validation. */
    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {

        ProblemDetail problem = problems.of(HttpStatus.BAD_REQUEST, "error.malformed.title",
                problems.message("error.malformed.detail"), List.of(), instanceOf(request));
        return ResponseEntity.badRequest().body(problem);
    }

    static URI instanceOf(WebRequest request) {
        return URI.create(((ServletWebRequest) request).getRequest().getRequestURI());
    }

    static URI instanceOf(HttpServletRequest request) {
        return URI.create(request.getRequestURI());
    }

    /** Service-layer {@code @Validated} method-parameter violation. */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ProblemDetail> handleConstraintViolation(
            ConstraintViolationException ex, HttpServletRequest request) {

        List<ValidationErrorDetail> errors = ex.getConstraintViolations().stream()
                .map(ValidationErrorDetail::of)
                .toList();
        return ResponseEntity.badRequest().body(problems.validation(errors, instanceOf(request)));
    }

    /**
     * DB constraint violation - app-layer check skipped or a race lost. Tries
     * Hibernate's own constraint name before scanning the vendor root-cause message.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ProblemDetail> handleDataIntegrityViolation(
            DataIntegrityViolationException ex, HttpServletRequest request) {

        String constraintName = ex.getCause() instanceof org.hibernate.exception.ConstraintViolationException hce
                ? hce.getConstraintName() : null;
        String source = (constraintName == null || constraintName.isBlank())
                ? ex.getMostSpecificCause().getMessage() : constraintName;
        String detail = problems.message(messageKeyFor(source));
        ProblemDetail problem = problems.of(HttpStatus.CONFLICT, "error.conflict.title",
                detail, List.of(), instanceOf(request));
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
    }

    /** Business rules that cannot be expressed as annotations. */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ProblemDetail> handleBusinessException(
            BusinessException ex, HttpServletRequest request) {

        ProblemDetail problem = problems.of(ex.getStatus(), ex.getTitleKey(),
                problems.message(ex.getDetailKey(), ex.getArgs()), List.of(), instanceOf(request));
        return ResponseEntity.status(ex.getStatus()).body(problem);
    }

    private static String messageKeyFor(String rootMessage) {
        if (rootMessage == null) {
            return "error.dataintegrity.detail";
        }
        String upper = rootMessage.toUpperCase(Locale.ROOT);
        if (upper.contains("UK_USER_EMAIL")) {
            return "validation.user.email.unique";
        }
        if (upper.contains("UK_USER_TCKN")) {
            return "validation.user.tckn.unique";
        }
        if (upper.contains("USERS_AGE_CHECK")) {
            return "validation.user.age.check";
        }
        return "error.dataintegrity.detail";
    }
}
