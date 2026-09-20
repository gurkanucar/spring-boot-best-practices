package com.gucardev.validation.constraint;

import static org.assertj.core.api.Assertions.assertThat;

import com.gucardev.validation.user.Role;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class EnumValueValidatorTest {

    private static Validator validator;

    record CaseInsensitiveHolder(@EnumValue(enumClass = Role.class) String value) {}

    record CaseSensitiveHolder(@EnumValue(enumClass = Role.class, ignoreCase = false) String value) {}

    @BeforeAll
    static void setUp() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            validator = factory.getValidator();
        }
    }

    @Test
    void acceptsExactEnumConstant() {
        assertThat(validator.validate(new CaseSensitiveHolder("ADMIN"))).isEmpty();
    }

    @Test
    void acceptsDifferentCaseWhenIgnoreCaseIsOn() {
        assertThat(validator.validate(new CaseInsensitiveHolder("admin"))).isEmpty();
    }

    @Test
    void rejectsDifferentCaseWhenIgnoreCaseIsOff() {
        assertThat(validator.validate(new CaseSensitiveHolder("admin"))).hasSize(1);
    }

    @Test
    void rejectsUnknownConstant() {
        assertThat(validator.validate(new CaseInsensitiveHolder("SUPERUSER"))).hasSize(1);
    }

    @Test
    void treatsNullAsValid() {
        assertThat(validator.validate(new CaseInsensitiveHolder(null))).isEmpty();
    }
}
