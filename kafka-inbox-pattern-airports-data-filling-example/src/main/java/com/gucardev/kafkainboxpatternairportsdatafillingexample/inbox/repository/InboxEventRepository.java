package com.gucardev.kafkainboxpatternairportsdatafillingexample.inbox.repository;

import com.gucardev.kafkainboxpatternairportsdatafillingexample.inbox.entity.InboxEvent;
import com.gucardev.kafkainboxpatternairportsdatafillingexample.inbox.entity.InboxStatus;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

public interface InboxEventRepository extends JpaRepository<InboxEvent, Long> {

    /**
     * Claims the next due event. {@code FOR UPDATE} locks the row for this transaction;
     * {@code SKIP LOCKED} makes other instances skip rows that are already claimed instead of
     * waiting for them. Several application instances can therefore poll the same table safely,
     * each working on different events.
     */
    @Query(value = """
            select * from inbox_event
            where status = 'PENDING' and next_attempt_at <= :now
            order by id
            limit 1
            for update skip locked""", nativeQuery = true)
    Optional<InboxEvent> claimNext(Instant now);

    Optional<InboxEvent> findByTransactionId(String transactionId);

    List<InboxEvent> findByStatusOrderByIdDesc(InboxStatus status, Pageable pageable);

    List<InboxEvent> findAllByOrderByIdDesc(Pageable pageable);

    List<InboxEvent> findByAirportCodeOrderById(String airportCode);

    /**
     * Transactional here, on the repository method, not on the caller: InboxCleanupJob calls
     * its own method from the scheduled one, and a self-invocation bypasses the Spring proxy, so
     * a {@code @Transactional} there would silently not start a transaction.
     */
    @Transactional
    @Modifying
    @Query("delete from InboxEvent e where e.status in :statuses and e.processedAt < :before")
    int deleteFinishedBefore(Collection<InboxStatus> statuses, Instant before);
}
