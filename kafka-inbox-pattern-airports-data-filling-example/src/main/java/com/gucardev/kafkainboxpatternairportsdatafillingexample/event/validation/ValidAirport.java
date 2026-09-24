package com.gucardev.kafkainboxpatternairportsdatafillingexample.event.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Target(TYPE)
@Retention(RUNTIME)
@Constraint(validatedBy = AirportValidator.class)
public @interface ValidAirport {
    String message() default "Invalid airport";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
