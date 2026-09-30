package com.gucardev.jwtauthrefreshtokenroles.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SecureRandomTokenGeneratorTest {

    private final RandomTokenGenerator generator = new SecureRandomTokenGenerator();

    @Test
    void opaqueTokenIs256BitsBase64UrlWithoutPadding() {
        String token = generator.opaqueToken();
        // 32 bytes -> 43 base64url characters without padding.
        assertThat(token).hasSize(43).matches("[A-Za-z0-9_-]+");
    }

    @Test
    void opaqueTokensAreUnique() {
        Set<String> tokens = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            tokens.add(generator.opaqueToken());
        }
        assertThat(tokens).hasSize(1000);
    }

    @Test
    void numericCodeIsZeroPaddedToRequestedDigits() {
        for (int i = 0; i < 1000; i++) {
            assertThat(generator.numericCode(6)).matches("\\d{6}");
        }
    }

    @Test
    void rejectsUnsupportedDigitCounts() {
        assertThatThrownBy(() -> generator.numericCode(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> generator.numericCode(10)).isInstanceOf(IllegalArgumentException.class);
    }
}
