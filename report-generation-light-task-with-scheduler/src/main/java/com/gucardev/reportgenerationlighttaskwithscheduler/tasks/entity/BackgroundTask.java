package com.gucardev.reportgenerationlighttaskwithscheduler.tasks.entity;

import com.gucardev.reportgenerationlighttaskwithscheduler.tasks.repository.BackgroundTaskRepository;
import com.gucardev.reportgenerationlighttaskwithscheduler.tasks.service.TaskService;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Read-only mapping of a task row. Rows are inserted and moved between states with targeted SQL in
 * {@link TaskService} and {@link BackgroundTaskRepository}, so concurrent updates cannot overwrite
 * each other through stale entities.
 */
@Entity
@Table(name = "background_task")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BackgroundTask {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TaskType type;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TaskStatus status;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "max_attempts", nullable = false)
    private int maxAttempts;

    @Column(name = "run_at", nullable = false)
    private Instant runAt;

    @Column(name = "locked_at")
    private Instant lockedAt;

    @Column(name = "locked_by")
    private String lockedBy;

    @Column(name = "last_error")
    private String lastError;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "idempotency_key")
    private String idempotencyKey;
}
