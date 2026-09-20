package com.gucardev.validation.constraint;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class ValidDateRangeValidator implements ConstraintValidator<ValidDateRange, Object> {

    private String startField;
    private String endField;
    private String message;

    @Override
    public void initialize(ValidDateRange annotation) {
        this.startField = annotation.start();
        this.endField = annotation.end();
        this.message = annotation.message();
    }

    @Override
    public boolean isValid(Object value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }
        Object start = PropertyReader.read(value, startField);
        Object end = PropertyReader.read(value, endField);
        if (start == null || end == null) {
            return true;
        }
        if (!(start instanceof Comparable<?>)) {
            throw new IllegalStateException(
                    "@ValidDateRange requires a Comparable field: " + startField);
        }
        @SuppressWarnings("unchecked")
        Comparable<Object> comparableStart = (Comparable<Object>) start;
        if (comparableStart.compareTo(end) < 0) {
            return true;
        }
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(message)
                .addPropertyNode(endField)
                .addConstraintViolation();
        return false;
    }
}
