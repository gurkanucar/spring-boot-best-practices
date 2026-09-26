package com.gucardev.reportgenerationlighttaskwithscheduler.task;

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

    /**
     * Stores a PENDING task that is due at once and returns its id. Joins the caller's transaction,
     * so the task commits or rolls back together with the business data. An existing
     * {@code idempotencyKey} inserts nothing and returns the existing task's id.
     */
    @Transactional
    public UUID enqueue(BackgroundTask.Type type, Object payload, String idempotencyKey) {
        jdbc.sql("""
                        insert into background_task (id, type, payload, status, run_at, idempotency_key)
                        values (:id, :type, cast(:payload as jsonb), 'PENDING', now(), :key)
                        on conflict (idempotency_key) do nothing""")
                .param("id", UUID.randomUUID())
                .param("type", type.name())
                .param("payload", jsonMapper.writeValueAsString(payload))
                .param("key", idempotencyKey)
                .update();
        return jdbc.sql("select id from background_task where idempotency_key = :key")
                .param("key", idempotencyKey)
                .query(UUID.class)
                .single();
    }
}
