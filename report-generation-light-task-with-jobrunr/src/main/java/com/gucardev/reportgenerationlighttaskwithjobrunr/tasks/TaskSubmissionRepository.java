package com.gucardev.reportgenerationlighttaskwithjobrunr.tasks;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

public interface TaskSubmissionRepository extends JpaRepository<TaskSubmission, UUID> {

    List<TaskSubmission> findTop100BySubmittedAtIsNullOrderByCreatedAtAsc();

    @Transactional
    @Modifying
    @Query("update TaskSubmission s set s.submittedAt = :now where s.id = :id and s.submittedAt is null")
    int markSubmitted(UUID id, Instant now);
}
