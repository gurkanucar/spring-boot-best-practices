package com.gucardev.kafkainboxpatternairportsdatafillingexample.inbox.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "inbox_event")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InboxEvent {

    private static final int MAX_ERROR_LENGTH = 2000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "transaction_id", nullable = false, updatable = false)
    private String transactionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private InboxSource source;

    @Column(name = "airport_code", nullable = false, updatable = false)
    private String airportCode;

    @Column(nullable = false, updatable = false)
    private long version;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, updatable = false)
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InboxStatus status;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "last_error")
    private String lastError;

    @Column(name = "kafka_position", updatable = false)
    private String kafkaPosition;

    @Column(name = "received_at", nullable = false, updatable = false)
    private Instant receivedAt;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "processed_at")
    private Instant processedAt;

    public void markProcessed() {
        finish(InboxStatus.PROCESSED, null);
    }

    public void markSkipped() {
        finish(InboxStatus.SKIPPED, null);
    }

    public void markFailed(String error) {
        attempts++;
        finish(InboxStatus.FAILED, error);
    }

    public void recordFailedAttempt(String error, int maxAttempts, Duration backoff) {
        attempts++;
        lastError = truncate(error);
        if (attempts >= maxAttempts) {
            status = InboxStatus.FAILED;
            processedAt = Instant.now();
        } else {
            // linear backoff: 1x, 2x, 3x ... the configured delay
            nextAttemptAt = Instant.now().plus(backoff.multipliedBy(attempts));
        }
    }

    private void finish(InboxStatus status, String message) {
        this.status = status;
        this.lastError = truncate(message);
        this.processedAt = Instant.now();
    }

    private static String truncate(String message) {
        return message == null || message.length() <= MAX_ERROR_LENGTH ? message : message.substring(0, MAX_ERROR_LENGTH);
    }
}
