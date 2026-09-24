package com.gucardev.kafkaoutboxpatternflightdataupdatesexample.event.dto;

import com.gucardev.kafkaoutboxpatternflightdataupdatesexample.flight.entity.Flight;
import com.gucardev.kafkaoutboxpatternflightdataupdatesexample.flight.entity.FlightStatus;
import java.time.Instant;
import java.time.LocalDate;

/** The complete state of a flight after a change. Used in events and as the API response. */
public record FlightSnapshot(
        String flightId,
        String carrierCode,
        String flightNumber,
        LocalDate departureDate,
        String origin,
        String destination,
        FlightStatus status,
        Instant scheduledDeparture,
        Instant scheduledArrival,
        Instant estimatedDeparture,
        Instant estimatedArrival,
        Instant actualDeparture,
        Instant actualArrival,
        long departureDelayMinutes,
        String delayCode,
        String terminal,
        String gate,
        String aircraftRegistration,
        String aircraftType,
        String divertedTo,
        String cancellationReason,
        long version,
        Instant updatedAt) {

    public static FlightSnapshot from(Flight f) {
        return new FlightSnapshot(f.getId(), f.getCarrierCode(), f.getFlightNumber(), f.getDepartureDate(),
                f.getOrigin(), f.getDestination(), f.getStatus(), f.getScheduledDeparture(), f.getScheduledArrival(),
                f.getEstimatedDeparture(), f.getEstimatedArrival(), f.getActualDeparture(), f.getActualArrival(),
                f.departureDelayMinutes(), f.getDelayCode(), f.getTerminal(), f.getGate(),
                f.getAircraftRegistration(), f.getAircraftType(), f.getDivertedTo(), f.getCancellationReason(),
                f.getVersion(), f.getUpdatedAt());
    }
}
