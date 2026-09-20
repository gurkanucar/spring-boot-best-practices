package com.gucardev.restapidesign.error;

import org.springframework.validation.FieldError;

/** One errors[] entry; rejectedValue is null for non-field (business-rule) errors. */
public record ApiErrorDetail(String field, String code, String message, Object rejectedValue) {

    public static ApiErrorDetail of(FieldError error) {
        return new ApiErrorDetail(error.getField(), error.getCode(), error.getDefaultMessage(), error.getRejectedValue());
    }
}
