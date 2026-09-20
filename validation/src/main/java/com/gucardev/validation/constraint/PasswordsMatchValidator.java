package com.gucardev.validation.constraint;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.Objects;

public class PasswordsMatchValidator implements ConstraintValidator<PasswordsMatch, Object> {

    private String passwordField;
    private String passwordConfirmField;
    private String message;

    @Override
    public void initialize(PasswordsMatch annotation) {
        this.passwordField = annotation.password();
        this.passwordConfirmField = annotation.passwordConfirm();
        this.message = annotation.message();
    }

    @Override
    public boolean isValid(Object value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }
        Object password = PropertyReader.read(value, passwordField);
        Object passwordConfirm = PropertyReader.read(value, passwordConfirmField);
        // Leave presence checks to @NotBlank when either side is absent.
        if (password == null || passwordConfirm == null) {
            return true;
        }
        if (Objects.equals(password, passwordConfirm)) {
            return true;
        }
        // Replace the default whole-object violation with one on the confirm field.
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(message)
                .addPropertyNode(passwordConfirmField)
                .addConstraintViolation();
        return false;
    }
}
