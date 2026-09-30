package com.gucardev.jwtauthrefreshtokenroles.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gucardev.jwtauthrefreshtokenroles.common.config.AppSecurityProperties;
import com.gucardev.jwtauthrefreshtokenroles.common.error.BadRequestException;
import java.util.List;
import org.junit.jupiter.api.Test;

class MinLengthPasswordPolicyTest {

    private final PasswordPolicy policy = new MinLengthPasswordPolicy(
            new AppSecurityProperties(List.of(), "-", new AppSecurityProperties.Password(8)));

    @Test
    void acceptsPasswordOfMinimumLength() {
        assertThat(policy.check("12345678")).isEmpty();
        assertThatNoException().isThrownBy(() -> policy.validate("12345678"));
    }

    @Test
    void rejectsShortOrMissingPassword() {
        assertThat(policy.check("1234567")).contains("Password must be at least 8 characters");
        assertThat(policy.check(null)).isPresent();
        assertThatThrownBy(() -> policy.validate("short"))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Password must be at least 8 characters");
    }
}
