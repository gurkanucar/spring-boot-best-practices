package com.gucardev.kafkainboxpatternairportsdatafillingexample.airport.repository;

import com.gucardev.kafkainboxpatternairportsdatafillingexample.airport.entity.Airport;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface AirportRepository extends JpaRepository<Airport, String> {

    /**
     * {@code SELECT ... FOR UPDATE}: while one processor applies an event to this airport, a
     * second processor (another instance) handling another event of the same airport waits here.
     * The version comparison therefore always sees the latest committed version.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Airport a where a.code = :code")
    Optional<Airport> findByCodeForUpdate(String code);

    @EntityGraph(attributePaths = "runways")
    @Query("select a from Airport a where a.code = :code")
    Optional<Airport> findWithRunwaysByCode(String code);

    List<Airport> findAllByOrderByCode();
}
