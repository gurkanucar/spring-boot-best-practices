package com.gucardev.kafkaoutboxpatternflightdataupdatesexample.outbox.dto;

import com.gucardev.kafkaoutboxpatternflightdataupdatesexample.outbox.entity.OutboxStatus;
import java.time.Instant;
import java.util.UUID;

public record OutboxEventResponse(
        Long id,
        UUID eventId,
        String aggregateType,
        String aggregateId,
        String eventType,
        String topic,
        OutboxStatus status,
        int attempts,
        String lastError,
        String kafkaPosition,
        Instant createdAt,
        Instant nextAttemptAt,
        Instant sentAt) {
}
