package com.gucardev.jwtauthrefreshtokenroles.common.security;

/**
 * Hashes high-entropy secrets (refresh tokens, e-mail link tokens) before they are stored. A plain
 * fast hash is enough here because the input cannot be guessed; low-entropy codes use OtpCodeHasher.
 */
public interface TokenHasher {

    String hash(String token);
}
