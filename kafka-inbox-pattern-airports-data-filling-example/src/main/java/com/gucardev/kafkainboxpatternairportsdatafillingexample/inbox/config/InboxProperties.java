package com.gucardev.kafkainboxpatternairportsdatafillingexample.inbox.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.inbox")
public record InboxProperties(
        int maxAttempts,
        Duration retryBackoff,
        Duration retention) {
}
