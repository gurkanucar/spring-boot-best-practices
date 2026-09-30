package com.gucardev.jwtauthrefreshtokenroles.common.config;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("otp")
public record OtpProperties(String pepper, Duration cooldown, int maxAttempts, Ttl ttl) {

    private static final int MIN_PEPPER_BYTES = 32;

    public OtpProperties {
        // HMAC-SHA256 with a short key gives the numeric codes little protection if the table leaks.
        if (pepper == null || pepper.getBytes(StandardCharsets.UTF_8).length < MIN_PEPPER_BYTES) {
            throw new IllegalArgumentException("otp.pepper must be at least " + MIN_PEPPER_BYTES + " bytes");
        }
    }

    public record Ttl(Duration passwordReset, Duration emailVerification, Duration phoneVerification) {
    }
}
