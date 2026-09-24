package com.gucardev.entityauditing.common.audit;

import com.gucardev.entityauditing.common.error.ResourceNotFoundException;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** A revision across all entity types: who, when, and which entity types it touched. */
@RestController
@RequestMapping("/api/revisions")
public class RevisionController {

    public record RevisionResponse(Long revision, Instant at, String changedBy, Set<String> modifiedEntities) {
    }

    private final EntityManager entityManager;

    public RevisionController(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @GetMapping("/{revision}")
    @Transactional(readOnly = true)
    public RevisionResponse get(@PathVariable Long revision) {
        AuditRevision r = entityManager.find(AuditRevision.class, revision);
        if (r == null) {
            throw new ResourceNotFoundException("Revision " + revision + " not found");
        }
        return new RevisionResponse(r.getId(), r.getInstant(), r.getUsername(), new TreeSet<>(r.getModifiedEntityNames()));
    }
}
