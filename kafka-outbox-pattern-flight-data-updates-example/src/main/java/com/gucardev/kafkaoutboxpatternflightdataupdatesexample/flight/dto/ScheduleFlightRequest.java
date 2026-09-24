package com.gucardev.kafkaoutboxpatternflightdataupdatesexample.flight.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.Instant;
import java.time.LocalDate;

public record ScheduleFlightRequest(
        @NotBlank @Pattern(regexp = "[A-Z0-9]{2}", message = "must be a 2-character IATA airline code") String carrierCode,
        @NotBlank @Pattern(regexp = "\\d{1,4}", message = "must be 1 to 4 digits") String flightNumber,
        @NotNull LocalDate departureDate,
        @NotBlank @Pattern(regexp = "[A-Z]{3}", message = "must be a 3-letter IATA airport code") String origin,
        @NotBlank @Pattern(regexp = "[A-Z]{3}", message = "must be a 3-letter IATA airport code") String destination,
        @NotNull Instant scheduledDeparture,
        @NotNull Instant scheduledArrival,
        @Pattern(regexp = FlightPatterns.GATE, message = FlightPatterns.GATE_MESSAGE) String terminal,
        @Pattern(regexp = FlightPatterns.GATE, message = FlightPatterns.GATE_MESSAGE) String gate,
        @NotBlank @Pattern(regexp = FlightPatterns.REGISTRATION, message = FlightPatterns.REGISTRATION_MESSAGE)
        String aircraftRegistration,
        @NotBlank @Pattern(regexp = FlightPatterns.AIRCRAFT_TYPE, message = FlightPatterns.AIRCRAFT_TYPE_MESSAGE)
        String aircraftType) {
}
