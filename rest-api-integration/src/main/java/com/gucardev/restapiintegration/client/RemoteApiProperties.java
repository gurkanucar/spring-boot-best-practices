package com.gucardev.restapiintegration.client;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Everything needed to call the remote API; credentials come from configuration, never from code. */
@ConfigurationProperties("remote-api")
public record RemoteApiProperties(
        String baseUrl,
        String username,
        String password,
        String apiKey,
        Duration connectTimeout,
        Duration readTimeout) {
}
