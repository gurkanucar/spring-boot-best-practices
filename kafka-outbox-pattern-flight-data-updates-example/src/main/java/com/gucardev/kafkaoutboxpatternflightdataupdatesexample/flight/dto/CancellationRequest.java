package com.gucardev.kafkaoutboxpatternflightdataupdatesexample.flight.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CancellationRequest(@NotBlank @Size(max = 200) String reason) {
}
