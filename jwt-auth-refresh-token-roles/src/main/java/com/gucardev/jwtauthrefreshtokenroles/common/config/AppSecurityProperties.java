package com.gucardev.jwtauthrefreshtokenroles.common.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("security")
public record AppSecurityProperties(List<String> publicPaths, String tokenCleanupCron, Password password) {

    public record Password(int minLength) {
    }
}
