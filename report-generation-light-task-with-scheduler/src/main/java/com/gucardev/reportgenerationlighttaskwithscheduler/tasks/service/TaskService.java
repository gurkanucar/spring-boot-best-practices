package com.gucardev.reportgenerationlighttaskwithscheduler.tasks.service;

import com.gucardev.reportgenerationlighttaskwithscheduler.tasks.config.TaskProperties;
import com.gucardev.reportgenerationlighttaskwithscheduler.tasks.dto.ClaimedTask;
import com.gucardev.reportgenerationlighttaskwithscheduler.tasks.entity.BackgroundTask;
import com.gucardev.reportgenerationlighttaskwithscheduler.tasks.entity.TaskType;
import com.gucardev.reportgenerationlighttaskwithscheduler.tasks.handler.ReportGenerationHandler;
import com.gucardev.reportgenerationlighttaskwithscheduler.tasks.repository.BackgroundTaskRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
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
     * Rows locked by a concurrent claim are skipped, so two claims never get the same task.
     */
    @Transactional
    public List<ClaimedTask> claim(int limit) {
        List<UUID> ids = repository.lockEligibleIds(limit);
        if (ids.isEmpty()) {
            return List.of();
        }
        repository.markRunning(ids, properties.instanceId());
        return repository.findAllById(ids).stream().map(ClaimedTask::from).toList();
    }
}
