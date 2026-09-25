package com.gucardev.reportgenerationlighttaskwithjobrunr.tasks;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@Service
@RequiredArgsConstructor
public class TaskService {

    private final TaskSubmissionRepository submissions;
    private final JsonMapper json;

    /**
     * Joins the caller's business transaction. For concurrent calls with the same key,
     * the caller must serialize creation (e.g. lock the related business row).
     */
    @Transactional
    public UUID submit(TaskType type, Object payload, String idempotencyKey) {
        UUID id = idFor(idempotencyKey);
        if (!submissions.existsById(id)) {
            submissions.save(TaskSubmission.create(id, type, json.writeValueAsString(payload)));
        }
        return id;
    }

    public static UUID idFor(String key) {
        return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8));
    }
}
