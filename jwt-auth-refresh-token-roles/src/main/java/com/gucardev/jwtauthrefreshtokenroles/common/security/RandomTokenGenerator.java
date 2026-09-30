package com.gucardev.jwtauthrefreshtokenroles.common.security;

public interface RandomTokenGenerator {

    /** 256 random bits, base64url without padding (43 characters). */
    String opaqueToken();

    /** A zero-padded decimal code of the given length (1-9 digits). */
    String numericCode(int digits);
}
