package com.gucardev.kafkainboxpatternairportsdatafillingexample.airport.service;

import com.gucardev.kafkainboxpatternairportsdatafillingexample.airport.entity.Airport;
import com.gucardev.kafkainboxpatternairportsdatafillingexample.airport.repository.AirportRepository;
import com.gucardev.kafkainboxpatternairportsdatafillingexample.event.dto.AirportEvent;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class AirportSnapshotApplier {

    private final AirportRepository repository;
    private final EntityManager entityManager;

    @Transactional(propagation = Propagation.MANDATORY)
    public boolean apply(AirportEvent event) {
        var existing = repository.findByCodeForUpdate(event.airportCode());

        if (existing.isEmpty()) {
            // persist, not save(): the id is assigned, so save() would merge (an extra SELECT).
            // Two processors creating the same new airport at once: one gets a primary key
            // violation, its event is retried and then compared against the stored version.
            entityManager.persist(Airport.create(event.airportCode(), event.airport(), event.version(), event.transactionId()));
            return true;
        }

        Airport airport = existing.get();
        if (event.version() <= airport.getVersion()) {
            // Older or equal version: a late or replayed event. Full snapshots make this safe to drop.
            return false;
        }
        airport.applySnapshot(event.airport(), event.version(), event.transactionId());
        return true;
    }
}
