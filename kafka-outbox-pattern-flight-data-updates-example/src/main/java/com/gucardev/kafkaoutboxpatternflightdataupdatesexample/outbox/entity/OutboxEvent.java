package com.gucardev.kafkaoutboxpatternflightdataupdatesexample.outbox.entity;

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
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "outbox_event")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OutboxEvent {

    private static final int MAX_ERROR_LENGTH = 2000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;

    @Column(name = "aggregate_type", nullable = false, updatable = false)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false, updatable = false)
    private String aggregateId;

    @Column(name = "event_type", nullable = false, updatable = false)
    private String eventType;

    @Column(nullable = false, updatable = false)
    private String topic;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, updatable = false)
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OutboxStatus status;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "last_error")
    private String lastError;

    @Column(name = "kafka_position")
    private String kafkaPosition;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    public static OutboxEvent pending(UUID eventId, String aggregateType, String aggregateId, String eventType,
                                      String topic, String payload) {
        OutboxEvent event = new OutboxEvent();
        event.eventId = eventId;
        event.aggregateType = aggregateType;
        event.aggregateId = aggregateId;
        event.eventType = eventType;
        event.topic = topic;
        event.payload = payload;
        event.status = OutboxStatus.PENDING;
        event.createdAt = Instant.now();
        event.nextAttemptAt = event.createdAt;
        return event;
    }

    public void markSent(String kafkaPosition) {
        this.status = OutboxStatus.SENT;
        this.lastError = null;
        this.kafkaPosition = kafkaPosition;
        this.sentAt = Instant.now();
    }

    /** Exponential backoff: base, 2x, 4x ... capped at {@code max}. The event stays PENDING. */
    public void recordFailedAttempt(String error, Duration base, Duration max) {
        attempts++;
        lastError = error == null || error.length() <= MAX_ERROR_LENGTH ? error : error.substring(0, MAX_ERROR_LENGTH);
        Duration delay = base.multipliedBy(1L << Math.min(attempts - 1, 20));
        nextAttemptAt = Instant.now().plus(delay.compareTo(max) > 0 ? max : delay);
    }
}
