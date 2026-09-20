package com.gucardev.validation.constraint;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Runs without a Spring context, using plain Bean Validation.
 * Messages stay as raw {@code {key}} here; only violation count is asserted.
 */
class TcKimlikNoValidatorTest {

    private static Validator validator;

    record Holder(@TcKimlikNo String value) {}

    @BeforeAll
    static void setUp() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            validator = factory.getValidator();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"10000000146", "12345678950"})
    void acceptsValidIdentityNumbers(String value) {
        assertThat(validator.validate(new Holder(value))).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "11111111111",   // checksum fails
            "01234567890",   // first digit is 0
            "1234567895",    // 10 digits
            "123456789501",  // 12 digits
            "1234567895a"    // non-digit character
    })
    void rejectsInvalidIdentityNumbers(String value) {
        assertThat(validator.validate(new Holder(value))).hasSize(1);
    }

    @Test
    void treatsNullAsValidSoItComposesWithNotNull() {
        assertThat(validator.validate(new Holder(null))).isEmpty();
    }
}
