package com.gucardev.kafkainboxpatternairportsdatafillingexample.event.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/**
 * The contract for a change to an airport, the same for Kafka and REST.
 *
 * <p>Every event is a FULL SNAPSHOT of the airport, not a diff. That is what makes the
 * processing simple: the event with the highest {@code version} wins, and an older one arriving
 * late can simply be skipped.
 *
 * @param transactionId unique id of this change, chosen by the sender; the idempotency key
 * @param airportCode   IATA code; also the Kafka message key, so one airport's events stay in order
 * @param version       the sender's version of the airport, increasing with every change
 * @param occurredAt    when the change happened at the source (informational)
 * @param airport       the full state of the airport after the change
 */
public record AirportEvent(
        @NotBlank @Size(max = 100) String transactionId,
        @NotBlank @Pattern(regexp = "[A-Z]{3}", message = "must be a 3-letter IATA code") String airportCode,
        @NotNull @Positive Long version,
        Instant occurredAt,
        @NotNull @Valid AirportPayload airport) {
}
