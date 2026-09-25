package com.gucardev.reportgenerationlighttaskwithjobrunr.report;

import java.time.Instant;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ReportRequestRepository extends JpaRepository<ReportRequest, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from ReportRequest r where r.id = :id")
    Optional<ReportRequest> findForUpdate(Long id);

    long countByRequestedByAndCreatedAtAfter(String requestedBy, Instant since);

    Optional<ReportRequest> findFirstByRequestedByAndCreatedAtAfterOrderByCreatedAtAsc(String requestedBy, Instant since);

    /**
     * Serializes report requests of one user until the transaction ends, so a double click cannot
     * pass the limit check twice at the same moment. Other users are not blocked.
     */
    @Query(value = "select 1 from (select pg_advisory_xact_lock(hashtext(:requestedBy))) as locked", nativeQuery = true)
    Integer lockRequestsOf(String requestedBy);
}
