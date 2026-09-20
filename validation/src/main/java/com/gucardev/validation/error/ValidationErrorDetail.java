package com.gucardev.validation.error;

import jakarta.validation.ConstraintViolation;
import java.util.Locale;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;

/**
 * One validation error; elements of the {@code errors[]} array in an error response.
 *
 * @param field         the offending field, including nested paths ({@code address.city}, {@code contacts[0].value})
 * @param code          the violated constraint name ({@code NotBlank}, {@code Email}, {@code TcKimlikNo} ...)
 * @param message       user-facing message, resolved for the request locale
 * @param rejectedValue the rejected value; masked for password-like fields
 */
public record ValidationErrorDetail(String field, String code, String message, Object rejectedValue) {

    private static final String MASK = "***";

    public static ValidationErrorDetail of(FieldError error) {
        return new ValidationErrorDetail(
                error.getField(),
                error.getCode() == null ? "Invalid" : error.getCode(),
                error.getDefaultMessage(),
                mask(error.getField(), error.getRejectedValue()));
    }

    /** Class-level (global) errors carry the object name instead of a field name. */
    public static ValidationErrorDetail of(ObjectError error) {
        return new ValidationErrorDetail(
                error.getObjectName(),
                error.getCode() == null ? "Invalid" : error.getCode(),
                error.getDefaultMessage(),
                null);
    }

    public static ValidationErrorDetail of(ConstraintViolation<?> violation) {
        String field = lastNodeOf(violation.getPropertyPath().toString());
        String code = violation.getConstraintDescriptor().getAnnotation().annotationType().getSimpleName();
        return new ValidationErrorDetail(field, code, violation.getMessage(),
                mask(field, violation.getInvalidValue()));
    }

    /** For {@code HandlerMethodValidationException}: parameter name + resolved error. */
    public static ValidationErrorDetail of(String field, MessageSourceResolvable error, Object rejectedValue) {
        String[] codes = error.getCodes();
        String code = (codes == null || codes.length == 0) ? "Invalid" : codes[codes.length - 1];
        return new ValidationErrorDetail(field, code, error.getDefaultMessage(), mask(field, rejectedValue));
    }

    /** Takes the last segment of paths like {@code createUser.arg0.email}. */
    private static String lastNodeOf(String propertyPath) {
        int lastDot = propertyPath.lastIndexOf('.');
        return lastDot < 0 ? propertyPath : propertyPath.substring(lastDot + 1);
    }

    private static Object mask(String field, Object value) {
        if (field != null && field.toLowerCase(Locale.ROOT).contains("password")) {
            return MASK;
        }
        return value;
    }
}
