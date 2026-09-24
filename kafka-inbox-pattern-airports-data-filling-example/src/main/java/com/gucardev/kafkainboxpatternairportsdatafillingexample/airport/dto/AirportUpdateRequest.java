package com.gucardev.kafkainboxpatternairportsdatafillingexample.airport.dto;

import com.gucardev.kafkainboxpatternairportsdatafillingexample.event.dto.AirportPayload;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public record AirportUpdateRequest(
        @NotBlank @Size(max = 100) String transactionId,
        @NotNull @Positive Long version,
        Instant occurredAt,
        @NotNull @Valid AirportPayload airport) {
}
