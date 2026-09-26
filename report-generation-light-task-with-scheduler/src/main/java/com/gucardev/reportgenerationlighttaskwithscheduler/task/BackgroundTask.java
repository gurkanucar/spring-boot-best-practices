package com.gucardev.reportgenerationlighttaskwithscheduler.task;

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
 * Read-only mapping of a task row. Rows are inserted and moved between states with targeted SQL
 * ({@link TaskService}, {@link BackgroundTaskRepository}), never by saving a possibly stale entity.
 */
@Entity
@Table(name = "background_task")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BackgroundTask {

    public enum Status { PENDING, RUNNING, SUCCEEDED, DEAD }

    public enum Type { GENERATE_REPORT, SEND_REPORT_READY_EMAIL }

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Type type;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "max_attempts", nullable = false)
    private int maxAttempts;

    @Column(name = "run_at", nullable = false)
    private Instant runAt;

    @Column(name = "locked_at")
    private Instant lockedAt;

    @Column(name = "last_error")
    private String lastError;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "idempotency_key", nullable = false)
    private String idempotencyKey;
}
