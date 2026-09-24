package com.gucardev.kafkaoutboxpatternflightdataupdatesexample.flight.entity;

import static com.gucardev.kafkaoutboxpatternflightdataupdatesexample.flight.entity.FlightStatus.ARRIVED;
import static com.gucardev.kafkaoutboxpatternflightdataupdatesexample.flight.entity.FlightStatus.BOARDING;
import static com.gucardev.kafkaoutboxpatternflightdataupdatesexample.flight.entity.FlightStatus.CANCELLED;
import static com.gucardev.kafkaoutboxpatternflightdataupdatesexample.flight.entity.FlightStatus.DEPARTED;
import static com.gucardev.kafkaoutboxpatternflightdataupdatesexample.flight.entity.FlightStatus.DIVERTED;
import static com.gucardev.kafkaoutboxpatternflightdataupdatesexample.flight.entity.FlightStatus.SCHEDULED;

import com.gucardev.kafkaoutboxpatternflightdataupdatesexample.common.error.BusinessRuleException;
import com.gucardev.kafkaoutboxpatternflightdataupdatesexample.common.error.ConflictException;
import com.gucardev.kafkaoutboxpatternflightdataupdatesexample.event.dto.FlightChange;
import com.gucardev.kafkaoutboxpatternflightdataupdatesexample.flight.dto.ScheduleFlightRequest;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Every state-changing method checks the lifecycle, increments {@link #version} and returns the
 * {@link FlightChange} to publish, or {@code null} when nothing changed.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Flight {

    @Id
    @Column(length = 20)
    private String id;

    @Column(name = "carrier_code", nullable = false, length = 2)
    private String carrierCode;

    @Column(name = "flight_number", nullable = false, length = 4)
    private String flightNumber;

    @Column(name = "departure_date", nullable = false)
    private LocalDate departureDate;

    @Column(nullable = false, length = 3)
    private String origin;

    @Column(nullable = false, length = 3)
    private String destination;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FlightStatus status;

    @Column(name = "scheduled_departure", nullable = false)
    private Instant scheduledDeparture;

    @Column(name = "scheduled_arrival", nullable = false)
    private Instant scheduledArrival;

    @Column(name = "estimated_departure", nullable = false)
    private Instant estimatedDeparture;

    @Column(name = "estimated_arrival", nullable = false)
    private Instant estimatedArrival;

    @Column(name = "actual_departure")
    private Instant actualDeparture;

    @Column(name = "actual_arrival")
    private Instant actualArrival;

    @Column(name = "delay_code", length = 2)
    private String delayCode;

    @Column(length = 6)
    private String terminal;

    @Column(length = 6)
    private String gate;

    @Column(name = "aircraft_registration", nullable = false, length = 10)
    private String aircraftRegistration;

    @Column(name = "aircraft_type", nullable = false, length = 4)
    private String aircraftType;

    @Column(name = "diverted_to", length = 3)
    private String divertedTo;

    @Column(name = "cancellation_reason", length = 200)
    private String cancellationReason;

    // Incremented by this class on every change and published with each event. Not a JPA @Version:
    // commands lock the row, and the event needs the new value before the transaction flushes.
    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Carrier, number, flight date and origin identify a flight leg, e.g. TK1971-20260924-IST. */
    public static String idOf(String carrierCode, String flightNumber, LocalDate departureDate, String origin) {
        return carrierCode + flightNumber + "-" + departureDate.format(DateTimeFormatter.BASIC_ISO_DATE) + "-" + origin;
    }

    public static Flight schedule(ScheduleFlightRequest request) {
        if (request.origin().equals(request.destination())) {
            throw new BusinessRuleException("destination must differ from origin");
        }
        if (!request.scheduledArrival().isAfter(request.scheduledDeparture())) {
            throw new BusinessRuleException("scheduledArrival must be after scheduledDeparture");
        }
        Flight flight = new Flight();
        flight.id = idOf(request.carrierCode(), request.flightNumber(), request.departureDate(), request.origin());
        flight.carrierCode = request.carrierCode();
        flight.flightNumber = request.flightNumber();
        flight.departureDate = request.departureDate();
        flight.origin = request.origin();
        flight.destination = request.destination();
        flight.status = SCHEDULED;
        flight.scheduledDeparture = request.scheduledDeparture();
        flight.scheduledArrival = request.scheduledArrival();
        flight.estimatedDeparture = request.scheduledDeparture();
        flight.estimatedArrival = request.scheduledArrival();
        flight.terminal = request.terminal();
        flight.gate = request.gate();
        flight.aircraftRegistration = request.aircraftRegistration();
        flight.aircraftType = request.aircraftType();
        flight.createdAt = Instant.now();
        flight.touch();
        return flight;
    }

    public FlightChange.Scheduled scheduled() {
        return new FlightChange.Scheduled(scheduledDeparture, scheduledArrival);
    }

    /**
     * Sets a new estimated departure. Without an explicit estimated arrival, the arrival moves by the
     * same amount, keeping the planned block time.
     */
    public FlightChange.Delayed delay(Instant newEstimatedDeparture, Instant newEstimatedArrival,
                                      String delayCode, String reason) {
        requireStatus("delay", SCHEDULED, BOARDING);
        if (newEstimatedDeparture.isBefore(scheduledDeparture)) {
            throw new BusinessRuleException("estimatedDeparture must not be before scheduledDeparture");
        }
        Instant arrival = newEstimatedArrival != null ? newEstimatedArrival
                : estimatedArrival.plus(Duration.between(estimatedDeparture, newEstimatedDeparture));
        if (!arrival.isAfter(newEstimatedDeparture)) {
            throw new BusinessRuleException("estimatedArrival must be after estimatedDeparture");
        }
        Instant previous = estimatedDeparture;
        this.estimatedDeparture = newEstimatedDeparture;
        this.estimatedArrival = arrival;
        this.delayCode = delayCode;
        touch();
        return new FlightChange.Delayed(previous, estimatedDeparture, estimatedArrival, departureDelayMinutes(),
                delayCode, reason);
    }

    public FlightChange.GateChanged changeGate(String newTerminal, String newGate) {
        requireStatus("change the gate of", SCHEDULED, BOARDING);
        if (Objects.equals(terminal, newTerminal) && Objects.equals(gate, newGate)) {
            return null;
        }
        var change = new FlightChange.GateChanged(terminal, gate, newTerminal, newGate);
        this.terminal = newTerminal;
        this.gate = newGate;
        touch();
        return change;
    }

    /** A tail swap: another aircraft operates the flight. */
    public FlightChange.AircraftChanged changeAircraft(String registration, String type) {
        requireStatus("change the aircraft of", SCHEDULED, BOARDING);
        if (aircraftRegistration.equals(registration) && aircraftType.equals(type)) {
            return null;
        }
        var change = new FlightChange.AircraftChanged(aircraftRegistration, aircraftType, registration, type);
        this.aircraftRegistration = registration;
        this.aircraftType = type;
        touch();
        return change;
    }

    public FlightChange.BoardingStarted startBoarding() {
        requireStatus("start boarding of", SCHEDULED);
        if (gate == null) {
            throw new BusinessRuleException("a gate must be assigned before boarding");
        }
        this.status = BOARDING;
        touch();
        return new FlightChange.BoardingStarted(terminal, gate);
    }

    /** Off-block: the aircraft has left the gate. */
    public FlightChange.Departed depart(Instant at) {
        requireStatus("depart", BOARDING);
        this.status = DEPARTED;
        this.actualDeparture = at;
        touch();
        return new FlightChange.Departed(at, departureDelayMinutes());
    }

    /** On-block: at the destination, or at the diversion airport for a diverted flight. */
    public FlightChange.Arrived arrive(Instant at) {
        requireStatus("record the arrival of", DEPARTED, DIVERTED);
        if (!at.isAfter(actualDeparture)) {
            throw new BusinessRuleException("actualArrival must be after actualDeparture");
        }
        String airport = status == DIVERTED ? divertedTo : destination;
        this.status = ARRIVED;
        this.actualArrival = at;
        touch();
        return new FlightChange.Arrived(at, airport);
    }

    public FlightChange.Cancelled cancel(String reason) {
        requireStatus("cancel", SCHEDULED, BOARDING);
        this.status = CANCELLED;
        this.cancellationReason = reason;
        touch();
        return new FlightChange.Cancelled(reason);
    }

    public FlightChange.Diverted divert(String airport, String reason) {
        requireStatus("divert", DEPARTED);
        if (airport.equals(destination)) {
            throw new BusinessRuleException("divertedTo must differ from the destination");
        }
        this.status = DIVERTED;
        this.divertedTo = airport;
        touch();
        return new FlightChange.Diverted(airport, reason);
    }

    /** Minutes behind schedule: the actual departure once departed, the estimate before that. */
    public long departureDelayMinutes() {
        Instant reference = actualDeparture != null ? actualDeparture : estimatedDeparture;
        return Math.max(0, Duration.between(scheduledDeparture, reference).toMinutes());
    }

    private void requireStatus(String action, FlightStatus... allowed) {
        if (!Arrays.asList(allowed).contains(status)) {
            throw new ConflictException("Cannot %s flight %s in status %s".formatted(action, id, status));
        }
    }

    private void touch() {
        this.version++;
        this.updatedAt = Instant.now();
    }
}
