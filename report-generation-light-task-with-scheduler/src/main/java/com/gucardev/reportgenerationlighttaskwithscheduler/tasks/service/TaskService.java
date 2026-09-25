package com.gucardev.reportgenerationlighttaskwithscheduler.tasks.service;

import com.gucardev.reportgenerationlighttaskwithscheduler.tasks.config.TaskProperties;
import com.gucardev.reportgenerationlighttaskwithscheduler.tasks.dto.ClaimedTask;
import com.gucardev.reportgenerationlighttaskwithscheduler.tasks.entity.BackgroundTask;
import com.gucardev.reportgenerationlighttaskwithscheduler.tasks.entity.TaskStatus;
import com.gucardev.reportgenerationlighttaskwithscheduler.tasks.entity.TaskType;
import com.gucardev.reportgenerationlighttaskwithscheduler.tasks.repository.BackgroundTaskRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@Service
@RequiredArgsConstructor
public class TaskService {

    private final JdbcClient jdbc;
    private final JsonMapper jsonMapper;
    private final BackgroundTaskRepository repository;
    private final TaskProperties properties;

    /**
     * Stores a PENDING task that is due immediately.
     *
     * <p>Joins the caller's transaction (REQUIRED): called from a {@code @Transactional} business
     * method, the task row commits or rolls back together with the business data. A task is never
     * started for data that was not saved, and saved data never misses its task.
     *
     * <p>With an {@code idempotencyKey} that already exists, nothing is inserted and the existing
     * task is returned. {@code ON CONFLICT} lets the unique index decide, so this also holds for
     * two concurrent calls.
     *
     * @param payload what the handler takes: an id ({@code reportRequest.getId()}) or a small record.
     *                Ids and small values only; handlers load the data they need.
     */
    @Transactional
    public BackgroundTask enqueue(TaskType type, Object payload, String idempotencyKey) {
        Optional<UUID> insertedId = jdbc.sql("""
                        insert into background_task (id, type, payload, status, run_at, idempotency_key)
                        values (:id, :type, cast(:payload as jsonb), 'PENDING', now(), :idempotencyKey)
                        on conflict (idempotency_key) do nothing
                        returning id""")
                .param("id", UUID.randomUUID())
                .param("type", type.name())
                .param("payload", jsonMapper.writeValueAsString(payload))
                .param("idempotencyKey", idempotencyKey)
                .query(UUID.class)
                .optional();

        return insertedId.flatMap(repository::findById)
                .or(() -> repository.findByIdempotencyKey(idempotencyKey))
                .orElseThrow();
    }

    /**
     * Claims up to {@code limit} due tasks for this instance: RUNNING, locked, attempts + 1.
     * Called by the ShedLock-protected poller. Full task types remain PENDING in the database;
     * no local permit counters and no claim/put-back loop for a full type.
     */
    @Transactional
    public List<ClaimedTask> claim(int limit) {
        List<ClaimedTask> claimed = new ArrayList<>();
        Instant now = Instant.now();
        for (TaskType type : TaskType.values()) {
            if (claimed.size() >= limit) {
                break;
            }
            long running = repository.countByTypeAndStatusAndLockedBy(
                    type, TaskStatus.RUNNING, properties.instanceId());
            int capacity = (int) Math.min(limit - claimed.size(), properties.concurrencyOf(type) - running);
            if (capacity <= 0) {
                continue;
            }
            for (BackgroundTask task : repository.findByTypeAndStatusAndRunAtLessThanEqualOrderByRunAtAsc(
                    type, TaskStatus.PENDING, now, PageRequest.of(0, capacity))) {
                if (repository.markRunning(task.getId(), properties.instanceId(), task.getAttempts(), now,
                        TaskStatus.PENDING, TaskStatus.RUNNING) == 1) {
                    claimed.add(new ClaimedTask(task.getId(), type, task.getPayload(),
                            task.getAttempts() + 1, task.getMaxAttempts()));
                }
            }
        }
        return claimed;
    }
}
