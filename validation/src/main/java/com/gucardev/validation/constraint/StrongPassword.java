package com.gucardev.validation.constraint;

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Password strength rule: at least {@code minLength} characters, an uppercase letter,
 * a lowercase letter, a digit and a special character. {@code null} is treated as valid.
 */
@Documented
@Target({FIELD, PARAMETER, ANNOTATION_TYPE})
@Retention(RUNTIME)
@Constraint(validatedBy = StrongPasswordValidator.class)
public @interface StrongPassword {

    int minLength() default 8;

    String message() default "{validation.user.password.weak}";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
