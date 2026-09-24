package com.gucardev.kafkaoutboxpatternflightdataupdatesexample.outbox.repository;

import com.gucardev.kafkaoutboxpatternflightdataupdatesexample.outbox.entity.OutboxEvent;
import com.gucardev.kafkaoutboxpatternflightdataupdatesexample.outbox.entity.OutboxStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    // Only the oldest pending event of a flight is eligible. Locking it blocks later versions
    // from being claimed by other instances until it has been sent and committed.
    @Query(value = """
            select o.* from outbox_event o
            where o.status = 'PENDING' and o.next_attempt_at <= :now
              and not exists (select 1 from outbox_event earlier
                              where earlier.aggregate_type = o.aggregate_type
                                and earlier.aggregate_id = o.aggregate_id
                                and earlier.status = 'PENDING'
                                and earlier.id < o.id)
            order by o.id
            limit 1
            for update of o skip locked""", nativeQuery = true)
    Optional<OutboxEvent> claimNext(Instant now);

    List<OutboxEvent> findByAggregateIdOrderById(String aggregateId);
    List<OutboxEvent> findByAggregateIdAndStatusOrderById(String aggregateId, OutboxStatus status);
    List<OutboxEvent> findByStatusOrderByIdDesc(OutboxStatus status, Pageable pageable);
    List<OutboxEvent> findAllByOrderByIdDesc(Pageable pageable);

    // The transaction commits before the cleanup method releases its ShedLock.
    @Transactional(timeout = 120)
    @Modifying
    @Query(value = "delete from outbox_event where status = 'SENT' and sent_at < :before", nativeQuery = true)
    int deleteSentBefore(Instant before);
}
