package com.gucardev.jwtauthrefreshtokenroles.common.security;

import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.stereotype.Component;

@Component
public class SecureRandomTokenGenerator implements RandomTokenGenerator {

    private static final int TOKEN_BYTES = 32;

    private final SecureRandom random = new SecureRandom();

    @Override
    public String opaqueToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    @Override
    public String numericCode(int digits) {
        if (digits < 1 || digits > 9) {
            throw new IllegalArgumentException("digits must be between 1 and 9");
        }
        int bound = (int) Math.pow(10, digits);
        return String.format("%0" + digits + "d", random.nextInt(bound));
    }
}
