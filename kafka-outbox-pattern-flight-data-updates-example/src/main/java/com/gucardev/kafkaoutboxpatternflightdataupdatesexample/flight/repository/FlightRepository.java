package com.gucardev.kafkaoutboxpatternflightdataupdatesexample.flight.repository;

import com.gucardev.kafkaoutboxpatternflightdataupdatesexample.flight.entity.Flight;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface FlightRepository extends JpaRepository<Flight, String> {

    // Commands on one flight run one after another. This also makes the flight's outbox rows get
    // ascending ids in commit order, which the relay relies on.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select f from Flight f where f.id = :id")
    Optional<Flight> findByIdForUpdate(String id);

    List<Flight> findAllByOrderByScheduledDepartureAscIdAsc();

    List<Flight> findByDepartureDateOrderByScheduledDepartureAscIdAsc(LocalDate departureDate);
}
