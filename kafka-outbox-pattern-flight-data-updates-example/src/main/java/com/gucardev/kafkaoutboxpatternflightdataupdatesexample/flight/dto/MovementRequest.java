package com.gucardev.kafkaoutboxpatternflightdataupdatesexample.flight.dto;

import java.time.Instant;

/** Off-block or on-block time of a departure or arrival. Defaults to now when omitted. */
public record MovementRequest(Instant at) {
}
