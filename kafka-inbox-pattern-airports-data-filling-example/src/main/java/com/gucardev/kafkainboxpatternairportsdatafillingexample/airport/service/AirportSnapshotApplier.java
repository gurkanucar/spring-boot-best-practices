package com.gucardev.kafkainboxpatternairportsdatafillingexample.airport.service;

import com.gucardev.kafkainboxpatternairportsdatafillingexample.airport.entity.Airport;
import com.gucardev.kafkainboxpatternairportsdatafillingexample.airport.repository.AirportRepository;
import com.gucardev.kafkainboxpatternairportsdatafillingexample.event.dto.AirportEvent;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The only code that writes airports. Applies a full snapshot if, and only if, it is newer than
 * what is stored ("highest version wins"). Runs inside the inbox processor's transaction.
 */
@Component
public class AirportSnapshotApplier {

    public enum Result { CREATED, UPDATED, STALE }

    public record Outcome(Result result, long currentVersion) {
    }

    private final AirportRepository repository;
    private final EntityManager entityManager;

    public AirportSnapshotApplier(AirportRepository repository, EntityManager entityManager) {
        this.repository = repository;
        this.entityManager = entityManager;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Outcome apply(AirportEvent event) {
        var existing = repository.findByCodeForUpdate(event.airportCode());

        if (existing.isEmpty()) {
            // persist, not save(): the id is assigned, so save() would merge (an extra SELECT).
            // Two processors creating the same new airport at once: one gets a primary key
            // violation, its event is retried and then compared against the stored version.
            entityManager.persist(Airport.create(event.airportCode(), event.airport(), event.version(), event.transactionId()));
            return new Outcome(Result.CREATED, event.version());
        }

        Airport airport = existing.get();
        if (event.version() <= airport.getVersion()) {
            // Older or equal version: a late or replayed event. Full snapshots make this safe to drop.
            return new Outcome(Result.STALE, airport.getVersion());
        }
        airport.applySnapshot(event.airport(), event.version(), event.transactionId());
        return new Outcome(Result.UPDATED, event.version());
    }
}
