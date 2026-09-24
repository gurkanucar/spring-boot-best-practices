package com.gucardev.reportgenerationlighttaskwithscheduler.tasks;

import tools.jackson.databind.JsonNode;

/**
 * Executes one type of task. Implementations are Spring beans; the runner finds them by {@link #type()}.
 *
 * <p><b>Handlers must be idempotent.</b> A task can run more than once: after a crash between the
 * handler's commit and the SUCCEEDED update, after stuck-task recovery, or after a retry that follows
 * a partially successful attempt. Running the same payload twice must not produce a second
 * report, a second email, a second charge...
 *
 * <p>{@link #handle} runs in its own transaction, which commits before the task is marked SUCCEEDED.
 * Throw {@link NonRetryableTaskException} for errors that retrying cannot fix; any other exception
 * is retried with backoff.
 */
public interface TaskHandler {

    TaskType type();

    void handle(JsonNode payload);
}
