package com.gucardev.entityauditing.common.audit;

import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.envers.ModifiedEntityNames;
import org.hibernate.envers.RevisionEntity;
import org.hibernate.envers.RevisionNumber;
import org.hibernate.envers.RevisionTimestamp;

/**
 * One row per transaction that changed audited data (replaces Envers' default {@code REVINFO}).
 * Every {@code *_history} row points here through its {@code rev} column, so "who and when" is
 * stored once per transaction, not once per changed entity.
 */
@Entity
@Table(name = "revision_info")
@RevisionEntity(AuditRevisionListener.class)
@Getter
public class AuditRevision {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @RevisionNumber
    private Long id;

    /** Epoch millis, filled by Envers. */
    @RevisionTimestamp
    private long timestamp;

    /** Filled by {@link AuditRevisionListener}. */
    @Setter
    @Column(nullable = false, length = 50)
    private String username;

    /** Entity names changed in this revision, filled by Envers because of {@code @ModifiedEntityNames}. */
    @ModifiedEntityNames
    @ElementCollection(fetch = FetchType.EAGER)
    @JoinTable(name = "revision_changed_entity", joinColumns = @JoinColumn(name = "rev"))
    @Column(name = "entity_name")
    private Set<String> modifiedEntityNames = new HashSet<>();

    public Instant getInstant() {
        return Instant.ofEpochMilli(timestamp);
    }
}
