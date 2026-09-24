package com.gucardev.kafkainboxpatternairportsdatafillingexample.airport.entity;

import com.gucardev.kafkainboxpatternairportsdatafillingexample.event.dto.AirportPayload;
import com.gucardev.kafkainboxpatternairportsdatafillingexample.event.dto.RunwayPayload;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.BatchSize;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Airport {

    /** IATA code, the natural key. Assigned by the source, not generated. */
    @Id
    @Column(length = 3)
    private String code;

    @Column(name = "icao_code", nullable = false, unique = true, length = 4)
    private String icaoCode;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(nullable = false, length = 100)
    private String city;

    @Column(name = "country_code", nullable = false, length = 2)
    private String countryCode;

    @Column(nullable = false, length = 50)
    private String timezone;

    /**
     * The SOURCE system's version of this airport, taken from the event. Deliberately not a JPA
     * {@code @Version}: we never increment it ourselves, we only compare incoming versions with it.
     */
    @Column(nullable = false)
    private long version;

    @Column(name = "last_transaction_id", nullable = false, length = 100)
    private String lastTransactionId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "airport", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("designator")
    @BatchSize(size = 50)
    private List<Runway> runways = new ArrayList<>();

    public static Airport create(String code, AirportPayload snapshot, long version, String transactionId) {
        Airport airport = new Airport();
        airport.code = code;
        airport.createdAt = Instant.now();
        airport.applySnapshot(snapshot, version, transactionId);
        return airport;
    }

    /** Replaces the whole state with the snapshot. Only called for a newer version. */
    public void applySnapshot(AirportPayload snapshot, long version, String transactionId) {
        this.icaoCode = snapshot.icaoCode();
        this.name = snapshot.name();
        this.city = snapshot.city();
        this.countryCode = snapshot.countryCode();
        this.timezone = snapshot.timezone();
        this.version = version;
        this.lastTransactionId = transactionId;
        this.updatedAt = Instant.now();
        syncRunways(snapshot.runways());
    }

    /**
     * Brings the runways in line with the snapshot by their natural key (the designator):
     * existing ones are updated in place, missing ones removed, new ones added.
     *
     * <p>Not "clear() and add all": Hibernate executes INSERTs before DELETEs when flushing, so
     * re-adding a runway with the same designator would hit the unique constraint
     * {@code (airport_code, designator)} before the old row is deleted. Updating in place also
     * keeps row ids stable and writes only what really changed.
     */
    private void syncRunways(List<RunwayPayload> incoming) {
        Map<String, RunwayPayload> byDesignator = incoming.stream()
                .collect(Collectors.toMap(RunwayPayload::designator, Function.identity()));
        runways.removeIf(r -> !byDesignator.containsKey(r.getDesignator()));
        Map<String, Runway> existing = runways.stream()
                .collect(Collectors.toMap(Runway::getDesignator, Function.identity()));
        for (RunwayPayload r : incoming) {
            Runway runway = existing.get(r.designator());
            if (runway != null) {
                runway.update(r.lengthMeters(), r.surface());
            } else {
                runways.add(new Runway(this, r.designator(), r.lengthMeters(), r.surface()));
            }
        }
    }

    public List<Runway> getRunways() {
        return Collections.unmodifiableList(runways);
    }
}
