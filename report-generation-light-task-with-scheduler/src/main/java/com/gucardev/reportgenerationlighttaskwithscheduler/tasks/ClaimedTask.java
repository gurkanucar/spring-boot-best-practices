package com.gucardev.reportgenerationlighttaskwithscheduler.tasks;

import java.util.UUID;

/**
 * What a worker needs to run a task. {@code attempts} is the value after the claim; together with
 * the instance id it identifies this execution, so a late status update from an execution that was
 * meanwhile recovered and re-claimed changes nothing.
 */
public record ClaimedTask(UUID id, TaskType type, String payload, int attempts, int maxAttempts) {

    static ClaimedTask from(BackgroundTask task) {
        return new ClaimedTask(task.getId(), task.getType(), task.getPayload(), task.getAttempts(), task.getMaxAttempts());
    }
}
