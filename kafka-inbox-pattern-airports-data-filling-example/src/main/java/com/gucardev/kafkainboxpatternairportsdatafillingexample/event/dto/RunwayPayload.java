package com.gucardev.kafkainboxpatternairportsdatafillingexample.event.dto;

import com.gucardev.kafkainboxpatternairportsdatafillingexample.airport.entity.RunwaySurface;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record RunwayPayload(
        @NotBlank @Size(max = 10) String designator,
        @Positive int lengthMeters,
        @NotNull RunwaySurface surface) {
}
