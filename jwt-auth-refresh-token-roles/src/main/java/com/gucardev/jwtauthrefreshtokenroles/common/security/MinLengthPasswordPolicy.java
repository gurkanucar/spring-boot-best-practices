package com.gucardev.jwtauthrefreshtokenroles.common.security;

import com.gucardev.jwtauthrefreshtokenroles.common.config.AppSecurityProperties;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MinLengthPasswordPolicy implements PasswordPolicy {

    private final AppSecurityProperties securityProperties;

    @Override
    public Optional<String> check(String password) {
        int minLength = securityProperties.password().minLength();
        if (password == null || password.length() < minLength) {
            return Optional.of("Password must be at least " + minLength + " characters");
        }
        return Optional.empty();
    }
}
