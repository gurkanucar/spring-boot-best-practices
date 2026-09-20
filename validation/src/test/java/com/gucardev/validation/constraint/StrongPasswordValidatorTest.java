package com.gucardev.validation.constraint;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class StrongPasswordValidatorTest {

    private static Validator validator;

    record Holder(@StrongPassword String value) {}

    @BeforeAll
    static void setUp() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            validator = factory.getValidator();
        }
    }

    @Test
    void acceptsStrongPassword() {
        assertThat(validator.validate(new Holder("Gucl!Parola1"))).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Ab1!",          // shorter than 8 characters
            "gucluparola1!", // no uppercase letter
            "GUCLUPAROLA1!", // no lowercase letter
            "GucluParola!",  // no digit
            "GucluParola1"   // no special character
    })
    void rejectsWeakPasswords(String value) {
        assertThat(validator.validate(new Holder(value))).hasSize(1);
    }

    @Test
    void treatsNullAsValid() {
        assertThat(validator.validate(new Holder(null))).isEmpty();
    }
}
