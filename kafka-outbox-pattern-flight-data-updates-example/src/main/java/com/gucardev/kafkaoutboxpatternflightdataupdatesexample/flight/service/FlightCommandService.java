package com.gucardev.kafkaoutboxpatternflightdataupdatesexample.flight.service;

import com.gucardev.kafkaoutboxpatternflightdataupdatesexample.common.error.ConflictException;
import com.gucardev.kafkaoutboxpatternflightdataupdatesexample.common.error.ResourceNotFoundException;
import com.gucardev.kafkaoutboxpatternflightdataupdatesexample.event.dto.FlightChange;
import com.gucardev.kafkaoutboxpatternflightdataupdatesexample.event.dto.FlightEvent;
import com.gucardev.kafkaoutboxpatternflightdataupdatesexample.event.dto.FlightSnapshot;
import com.gucardev.kafkaoutboxpatternflightdataupdatesexample.flight.dto.AircraftChangeRequest;
import com.gucardev.kafkaoutboxpatternflightdataupdatesexample.flight.dto.CancellationRequest;
import com.gucardev.kafkaoutboxpatternflightdataupdatesexample.flight.dto.DelayRequest;
import com.gucardev.kafkaoutboxpatternflightdataupdatesexample.flight.dto.DiversionRequest;
import com.gucardev.kafkaoutboxpatternflightdataupdatesexample.flight.dto.GateChangeRequest;
import com.gucardev.kafkaoutboxpatternflightdataupdatesexample.flight.dto.MovementRequest;
import com.gucardev.kafkaoutboxpatternflightdataupdatesexample.flight.dto.ScheduleFlightRequest;
import com.gucardev.kafkaoutboxpatternflightdataupdatesexample.flight.entity.Flight;
import com.gucardev.kafkaoutboxpatternflightdataupdatesexample.flight.repository.FlightRepository;
import com.gucardev.kafkaoutboxpatternflightdataupdatesexample.outbox.service.OutboxWriter;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Each command changes the flight and writes its event to the outbox in ONE database transaction:
 * either both are committed or neither is. Nothing here talks to Kafka.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class FlightCommandService {

    private final FlightRepository repository;
    private final EntityManager entityManager;
    private final OutboxWriter outbox;

    public FlightSnapshot schedule(ScheduleFlightRequest request) {
        Flight flight = Flight.schedule(request);
        if (repository.existsById(flight.getId())) {
            throw new ConflictException("Flight " + flight.getId() + " is already scheduled");
        }
        // persist, not save(): the id is assigned, so save() would merge (an extra SELECT).
        entityManager.persist(flight);
        return record(flight, flight.scheduled());
    }

    public FlightSnapshot delay(String id, DelayRequest request) {
        Flight flight = lock(id);
        return record(flight, flight.delay(request.estimatedDeparture(), request.estimatedArrival(),
                request.delayCode(), request.reason()));
    }

    public FlightSnapshot changeGate(String id, GateChangeRequest request) {
        Flight flight = lock(id);
        return record(flight, flight.changeGate(request.terminal(), request.gate()));
    }

    public FlightSnapshot changeAircraft(String id, AircraftChangeRequest request) {
        Flight flight = lock(id);
        return record(flight, flight.changeAircraft(request.registration(), request.type()));
    }

    public FlightSnapshot startBoarding(String id) {
        Flight flight = lock(id);
        return record(flight, flight.startBoarding());
    }

    public FlightSnapshot depart(String id, MovementRequest request) {
        Flight flight = lock(id);
        return record(flight, flight.depart(timeOf(request)));
    }

    public FlightSnapshot arrive(String id, MovementRequest request) {
        Flight flight = lock(id);
        return record(flight, flight.arrive(timeOf(request)));
    }

    public FlightSnapshot cancel(String id, CancellationRequest request) {
        Flight flight = lock(id);
        return record(flight, flight.cancel(request.reason()));
    }

    public FlightSnapshot divert(String id, DiversionRequest request) {
        Flight flight = lock(id);
        return record(flight, flight.divert(request.divertedTo(), request.reason()));
    }

    private Flight lock(String id) {
        return repository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Flight " + id + " not found"));
    }

    private FlightSnapshot record(Flight flight, FlightChange change) {
        FlightSnapshot snapshot = FlightSnapshot.from(flight);
        if (change != null) { // A no-op command does not create an event.
            outbox.append(new FlightEvent(UUID.randomUUID(), change.eventType(), flight.getId(),
                    flight.getVersion(), flight.getUpdatedAt(), snapshot, change));
        }
        return snapshot;
    }

    private static Instant timeOf(MovementRequest request) {
        return request != null && request.at() != null ? request.at() : Instant.now();
    }
}
