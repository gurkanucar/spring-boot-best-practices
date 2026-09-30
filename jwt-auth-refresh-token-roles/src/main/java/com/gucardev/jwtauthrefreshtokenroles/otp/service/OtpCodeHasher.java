package com.gucardev.jwtauthrefreshtokenroles.otp.service;

import java.util.UUID;

/**
 * Hashes low-entropy numeric codes. A 6-digit code has only 10^6 values, so a plain hash could be
 * reversed from a database dump in seconds; implementations must use a server-side secret.
 */
public interface OtpCodeHasher {

    String hash(UUID userId, OtpPurpose purpose, OtpChannel channel, String code);
}
