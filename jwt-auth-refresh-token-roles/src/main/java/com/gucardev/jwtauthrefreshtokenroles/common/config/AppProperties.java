package com.gucardev.jwtauthrefreshtokenroles.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app")
public record AppProperties(String baseUrl, Seed seed, Mail mail) {

    public record Seed(String adminEmail, String adminPassword) {
    }

    public record Mail(String from) {
    }
}
