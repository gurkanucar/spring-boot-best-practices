package com.gucardev.validation.constraint;

import static org.assertj.core.api.Assertions.assertThat;

import com.gucardev.validation.user.dto.CrossFieldUserRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.time.LocalDate;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class CrossFieldConstraintTest {

    private static Validator validator;

    @BeforeAll
    static void setUp() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            validator = factory.getValidator();
        }
    }

    private static CrossFieldUserRequest request(String password, String confirm,
                                                 LocalDate start, LocalDate end) {
        return new CrossFieldUserRequest(password, confirm, start, end);
    }

    @Test
    void acceptsMatchingPasswordsAndOrderedDates() {
        var violations = validator.validate(request("Gucl!Parola1", "Gucl!Parola1",
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)));

        assertThat(violations).isEmpty();
    }

    @Test
    void reportsMismatchOnPasswordConfirmField() {
        Set<ConstraintViolation<CrossFieldUserRequest>> violations = validator.validate(
                request("Gucl!Parola1", "BaskaParola1!",
                        LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)));

        assertThat(violations).singleElement()
                .extracting(v -> v.getPropertyPath().toString())
                .isEqualTo("passwordConfirm");
    }

    @Test
    void reportsInvalidRangeOnEndDateField() {
        var violations = validator.validate(request("Gucl!Parola1", "Gucl!Parola1",
                LocalDate.of(2026, 12, 31), LocalDate.of(2026, 1, 1)));

        assertThat(violations).singleElement()
                .extracting(v -> v.getPropertyPath().toString())
                .isEqualTo("endDate");
    }

    @Test
    void treatsNullFieldsAsValidSoOtherConstraintsReportThem() {
        var violations = validator.validate(request(null, null, null, null));

        assertThat(violations).isEmpty();
    }
}
