package com.gucardev.kafkaoutboxpatternflightdataupdatesexample.outbox.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.outbox")
public record OutboxProperties(
        int batchSize,
        Duration sendTimeout,
        Duration retryBackoff,
        Duration maxBackoff,
        Duration retention) {
}
