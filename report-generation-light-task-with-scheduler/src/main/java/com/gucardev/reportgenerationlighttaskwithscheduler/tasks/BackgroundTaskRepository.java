package com.gucardev.reportgenerationlighttaskwithscheduler.tasks;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * All state changes use the database clock ({@code now()}), so instances with skewed clocks agree.
 *
 * <p>The finishing updates only match the execution that owns the task: RUNNING, locked by this
 * instance, same attempt. They run in their own short transaction (REQUIRES_NEW), separate from
 * the handler's work.
 */
public interface BackgroundTaskRepository extends JpaRepository<BackgroundTask, UUID> {

    Optional<BackgroundTask> findByIdempotencyKey(String idempotencyKey);

    // SKIP LOCKED: rows another transaction is claiming right now are skipped, not waited for.
    @Query(value = """
            select id from background_task
            where status = 'PENDING' and run_at <= now()
            order by run_at
            limit :limit
            for update skip locked""", nativeQuery = true)
    List<UUID> lockEligibleIds(int limit);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            update background_task
            set status = 'RUNNING', locked_at = now(), locked_by = :instanceId,
                attempts = attempts + 1, updated_at = now()
            where id in (:ids)""", nativeQuery = true)
    int markRunning(Collection<UUID> ids, String instanceId);

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Modifying
    @Query(value = """
            update background_task
            set status = 'SUCCEEDED', locked_at = null, locked_by = null, updated_at = now()
            where id = :id and status = 'RUNNING' and locked_by = :instanceId and attempts = :attempts""",
            nativeQuery = true)
    int markSucceeded(UUID id, String instanceId, int attempts);

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Modifying
    @Query(value = """
            update background_task
            set status = 'PENDING', run_at = now() + :delaySeconds * interval '1 second',
                locked_at = null, locked_by = null, last_error = :error, updated_at = now()
            where id = :id and status = 'RUNNING' and locked_by = :instanceId and attempts = :attempts""",
            nativeQuery = true)
    int scheduleRetry(UUID id, String instanceId, int attempts, double delaySeconds, String error);

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Modifying
    @Query(value = """
            update background_task
            set status = 'DEAD', locked_at = null, locked_by = null, last_error = :error, updated_at = now()
            where id = :id and status = 'RUNNING' and locked_by = :instanceId and attempts = :attempts""",
            nativeQuery = true)
    int markDead(UUID id, String instanceId, int attempts, String error);

    /** Hands a claimed task back without running it: the claim's attempt is taken back. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Modifying
    @Query(value = """
            update background_task
            set status = 'PENDING', run_at = now() + :delaySeconds * interval '1 second',
                locked_at = null, locked_by = null, attempts = attempts - 1, updated_at = now()
            where id = :id and status = 'RUNNING' and locked_by = :instanceId and attempts = :attempts""",
            nativeQuery = true)
    int putBack(UUID id, String instanceId, int attempts, double delaySeconds);

    @Transactional
    @Modifying
    @Query(value = """
            update background_task
            set status = 'PENDING', run_at = now(), locked_at = null, locked_by = null,
                last_error = 'recovered after stall', updated_at = now()
            where status = 'RUNNING' and locked_at < now() - :stuckAfterSeconds * interval '1 second'""",
            nativeQuery = true)
    int recoverStuck(double stuckAfterSeconds);
}
