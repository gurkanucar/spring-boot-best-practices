package com.gucardev.kafkaoutboxpatternflightdataupdatesexample.flight.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record DiversionRequest(
        @NotBlank @Pattern(regexp = "[A-Z]{3}", message = "must be a 3-letter IATA airport code") String divertedTo,
        @NotBlank @Size(max = 200) String reason) {
}
