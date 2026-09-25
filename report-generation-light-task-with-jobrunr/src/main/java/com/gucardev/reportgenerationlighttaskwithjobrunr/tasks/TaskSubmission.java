package com.gucardev.reportgenerationlighttaskwithjobrunr.tasks;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Durable enqueue intent only. JobRunr owns execution and retry state. */
@Entity
@Table(name = "task_submission")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TaskSubmission {

    @Id
    private UUID id;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TaskType type;
    @Column(nullable = false, columnDefinition = "text")
    private String payload;
    @Column(nullable = false)
    private Instant createdAt;
    private Instant submittedAt;

    public static TaskSubmission create(UUID id, TaskType type, String payload) {
        var submission = new TaskSubmission();
        submission.id = id;
        submission.type = type;
        submission.payload = payload;
        submission.createdAt = Instant.now();
        return submission;
    }
}
