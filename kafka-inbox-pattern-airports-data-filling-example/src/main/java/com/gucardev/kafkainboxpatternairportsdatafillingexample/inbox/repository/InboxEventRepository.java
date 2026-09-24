package com.gucardev.kafkainboxpatternairportsdatafillingexample.inbox.repository;

import com.gucardev.kafkainboxpatternairportsdatafillingexample.inbox.entity.InboxEvent;
import com.gucardev.kafkainboxpatternairportsdatafillingexample.inbox.entity.InboxStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

public interface InboxEventRepository extends JpaRepository<InboxEvent, Long> {

    List<InboxEvent> findByStatusAndNextAttemptAtLessThanEqualOrderById(
            InboxStatus status, Instant now, Pageable pageable);

    // Selection alone does not claim an event. Lock and recheck before processing or recording a failure.
    @Query(value = """
            select * from inbox_event
            where id = :id and status = 'PENDING' and attempts = :attempts and next_attempt_at <= :now
            for update skip locked""", nativeQuery = true)
    Optional<InboxEvent> lockPending(long id, int attempts, Instant now);

    Optional<InboxEvent> findByTransactionId(String transactionId);
    List<InboxEvent> findByStatusOrderByIdDesc(InboxStatus status, Pageable pageable);
    List<InboxEvent> findAllByOrderByIdDesc(Pageable pageable);

    // The transaction commits before the cleanup method releases its ShedLock.
    @Transactional(timeout = 120)
    @Modifying
    @Query(value = """
            delete from inbox_event
            where status in ('PROCESSED', 'SKIPPED') and processed_at < :before
            """, nativeQuery = true)
    int deleteFinishedBefore(Instant before);
}
