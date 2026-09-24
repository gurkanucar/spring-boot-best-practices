package com.gucardev.kafkaoutboxpatternflightdataupdatesexample.flight.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/** {@code estimatedArrival} is optional: by default it moves together with the departure. */
public record DelayRequest(
        @NotNull Instant estimatedDeparture,
        Instant estimatedArrival,
        @NotBlank @Pattern(regexp = "\\d{2}", message = "must be a 2-digit IATA delay code") String delayCode,
        @Size(max = 200) String reason) {
}
