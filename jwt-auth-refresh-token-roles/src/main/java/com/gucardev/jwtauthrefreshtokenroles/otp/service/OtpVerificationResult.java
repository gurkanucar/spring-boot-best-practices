package com.gucardev.jwtauthrefreshtokenroles.otp.service;

import java.util.UUID;

/**
 * Outcome of a verification. A result instead of an exception, so a counted wrong attempt is never
 * mistaken for an error by the caller's transaction handling.
 */
public sealed interface OtpVerificationResult {

    OtpVerificationResult INVALID = new Invalid();

    record Valid(UUID userId) implements OtpVerificationResult {
    }

    record Invalid() implements OtpVerificationResult {
    }
}
