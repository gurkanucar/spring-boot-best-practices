package com.gucardev.reportgenerationlighttaskwithscheduler.tasks.repository;

import com.gucardev.reportgenerationlighttaskwithscheduler.tasks.entity.BackgroundTask;
import com.gucardev.reportgenerationlighttaskwithscheduler.tasks.entity.TaskStatus;
import com.gucardev.reportgenerationlighttaskwithscheduler.tasks.entity.TaskType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Claim uses JPA; the remaining native state updates belong to the PostgreSQL demo adapter.
 *
 * <p>The finishing updates only match the execution that owns the task: RUNNING, locked by this
 * instance, same attempt. They run in their own short transaction (REQUIRES_NEW), separate from
 * the handler's work.
 */
public interface BackgroundTaskRepository extends JpaRepository<BackgroundTask, UUID> {

    Optional<BackgroundTask> findByIdempotencyKey(String idempotencyKey);

    long countByTypeAndStatusAndLockedBy(TaskType type, TaskStatus status, String lockedBy);

    List<BackgroundTask> findByTypeAndStatusAndRunAtLessThanEqualOrderByRunAtAsc(
            TaskType type, TaskStatus status, Instant now, Pageable page);

    // The conditional update also protects a task if two dispatches ever overlap.
    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update BackgroundTask t
            set t.status = :running, t.lockedAt = :now, t.lockedBy = :instanceId,
                t.attempts = t.attempts + 1, t.updatedAt = :now
            where t.id = :id and t.status = :pending and t.attempts = :attempts""")
    int markRunning(UUID id, String instanceId, int attempts, Instant now,
                    TaskStatus pending, TaskStatus running);

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
            set status = case when attempts >= max_attempts then 'DEAD' else 'PENDING' end,
                run_at = now(), locked_at = null, locked_by = null,
                last_error = case when attempts >= max_attempts then 'attempt limit reached after stall'
                                  else 'recovered after stall' end, updated_at = now()
            where status = 'RUNNING' and locked_at < now() - :stuckAfterSeconds * interval '1 second'""",
            nativeQuery = true)
    int recoverStuck(double stuckAfterSeconds);
}
