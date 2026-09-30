package com.gucardev.jwtauthrefreshtokenroles.common.config;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class OtpPropertiesTest {

    private static final OtpProperties.Ttl TTL =
            new OtpProperties.Ttl(Duration.ofMinutes(15), Duration.ofHours(24), Duration.ofMinutes(5));

    @Test
    void rejectsPepperShorterThan32Bytes() {
        assertThatThrownBy(() -> new OtpProperties("too-short", Duration.ofSeconds(60), 5, TTL))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("otp.pepper");
    }

    @Test
    void rejectsMissingPepper() {
        assertThatThrownBy(() -> new OtpProperties(null, Duration.ofSeconds(60), 5, TTL))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void accepts32BytePepper() {
        assertThatNoException().isThrownBy(
                () -> new OtpProperties("x".repeat(32), Duration.ofSeconds(60), 5, TTL));
    }
}
