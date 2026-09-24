package com.gucardev.kafkaoutboxpatternflightdataupdatesexample.event.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * The message published to Kafka. The key is {@code flightId}.
 *
 * <p>{@code eventId} lets consumers drop redeliveries; {@code version} increases by one per change of
 * the flight, so a consumer can also ignore anything older than what it already has.
 */
public record FlightEvent(
        UUID eventId,
        FlightEventType eventType,
        String flightId,
        long version,
        Instant occurredAt,
        FlightSnapshot flight,
        FlightChange change) {
}
