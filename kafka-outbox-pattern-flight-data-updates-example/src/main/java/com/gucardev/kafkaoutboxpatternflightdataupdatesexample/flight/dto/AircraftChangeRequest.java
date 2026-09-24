package com.gucardev.kafkaoutboxpatternflightdataupdatesexample.flight.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record AircraftChangeRequest(
        @NotBlank @Pattern(regexp = FlightPatterns.REGISTRATION, message = FlightPatterns.REGISTRATION_MESSAGE)
        String registration,
        @NotBlank @Pattern(regexp = FlightPatterns.AIRCRAFT_TYPE, message = FlightPatterns.AIRCRAFT_TYPE_MESSAGE)
        String type) {
}
