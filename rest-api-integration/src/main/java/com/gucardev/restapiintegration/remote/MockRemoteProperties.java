package com.gucardev.restapiintegration.remote;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("mock-remote")
public record MockRemoteProperties(String username, String password, String apiKey, int httpsPort) {
}
