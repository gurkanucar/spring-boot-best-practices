package com.gucardev.kafkainboxpatternairportsdatafillingexample.event.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public record AirportEvent(
        @NotBlank @Size(max = 100) String transactionId,
        @NotBlank @Pattern(regexp = "[A-Z]{3}", message = "must be a 3-letter IATA code") String airportCode,
        @NotNull @Positive Long version,
        Instant occurredAt,
        @NotNull @Valid AirportPayload airport) {
}
