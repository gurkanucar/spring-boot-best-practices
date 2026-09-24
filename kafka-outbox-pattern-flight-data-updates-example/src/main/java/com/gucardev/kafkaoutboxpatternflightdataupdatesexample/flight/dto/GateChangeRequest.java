package com.gucardev.kafkaoutboxpatternflightdataupdatesexample.flight.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record GateChangeRequest(
        @NotBlank @Pattern(regexp = FlightPatterns.GATE, message = FlightPatterns.GATE_MESSAGE) String terminal,
        @NotBlank @Pattern(regexp = FlightPatterns.GATE, message = FlightPatterns.GATE_MESSAGE) String gate) {
}
