package com.gucardev.reportgenerationlighttaskwithscheduler.task;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

/**
 * The finishing updates only match the execution that claimed the task (RUNNING, same attempt),
 * so a late result from an execution that was recovered and claimed again changes nothing.
 */
public interface BackgroundTaskRepository extends JpaRepository<BackgroundTask, UUID> {

    /** ShedLock already serializes polling; SKIP LOCKED is a second guard if two polls ever overlap. */
    @Transactional
    @Query(value = """
            update background_task
            set status = 'RUNNING', locked_at = now(), attempts = attempts + 1, updated_at = now()
            where id in (select id from background_task
                         where status = 'PENDING' and run_at <= now()
                         order by run_at limit :limit
                         for update skip locked)
            returning *""", nativeQuery = true)
    List<BackgroundTask> claim(int limit);

    @Transactional
    @Modifying
    @Query(value = """
            update background_task
            set status = 'SUCCEEDED', locked_at = null, updated_at = now()
            where id = :id and status = 'RUNNING' and attempts = :attempts""", nativeQuery = true)
    int markSucceeded(UUID id, int attempts);

    /** Retries after {@code delaySeconds}, or gives up (DEAD) once the attempts are used up. */
    @Transactional
    @Modifying
    @Query(value = """
            update background_task
            set status = case when attempts >= max_attempts then 'DEAD' else 'PENDING' end,
                run_at = now() + :delaySeconds * interval '1 second',
                locked_at = null, last_error = :error, updated_at = now()
            where id = :id and status = 'RUNNING' and attempts = :attempts""", nativeQuery = true)
    int markFailed(UUID id, int attempts, double delaySeconds, String error);

    /** Hands a claimed task back without running it: the claim's attempt is taken back. */
    @Transactional
    @Modifying
    @Query(value = """
            update background_task
            set status = 'PENDING', locked_at = null, attempts = attempts - 1, updated_at = now()
            where id = :id and status = 'RUNNING' and attempts = :attempts""", nativeQuery = true)
    int putBack(UUID id, int attempts);

    @Transactional
    @Modifying
    @Query(value = """
            update background_task
            set status = case when attempts >= max_attempts then 'DEAD' else 'PENDING' end,
                run_at = now(), locked_at = null, last_error = 'recovered after stall', updated_at = now()
            where status = 'RUNNING' and locked_at < now() - :stuckAfterSeconds * interval '1 second'""",
            nativeQuery = true)
    int recoverStuck(double stuckAfterSeconds);
}
