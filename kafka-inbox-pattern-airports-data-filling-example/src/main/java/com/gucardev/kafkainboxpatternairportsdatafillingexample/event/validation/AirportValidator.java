package com.gucardev.kafkainboxpatternairportsdatafillingexample.event.validation;

import com.gucardev.kafkainboxpatternairportsdatafillingexample.event.dto.AirportPayload;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.time.ZoneId;
import java.util.HashSet;

public class AirportValidator implements ConstraintValidator<ValidAirport, AirportPayload> {

    @Override
    public boolean isValid(AirportPayload airport, ConstraintValidatorContext context) {
        if (airport == null) {
            return true;
        }
        context.disableDefaultConstraintViolation();
        boolean valid = true;
        if (airport.timezone() != null && !ZoneId.getAvailableZoneIds().contains(airport.timezone())) {
            violation(context, "timezone", "must be a known time zone, e.g. Europe/Istanbul");
            valid = false;
        }
        if (airport.runways() != null) {
            var designators = new HashSet<String>();
            for (var runway : airport.runways()) {
                // @NotNull reports null elements separately.
                if (runway != null && !designators.add(runway.designator())) {
                    violation(context, "runways", "runway designators must be unique");
                    valid = false;
                    break;
                }
            }
        }
        return valid;
    }

    private static void violation(ConstraintValidatorContext context, String field, String message) {
        context.buildConstraintViolationWithTemplate(message).addPropertyNode(field).addConstraintViolation();
    }
}
