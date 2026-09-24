package com.gucardev.reportgenerationlighttaskwithscheduler.tasks.handler;

import com.gucardev.reportgenerationlighttaskwithscheduler.tasks.entity.TaskType;
import com.gucardev.reportgenerationlighttaskwithscheduler.tasks.exception.NonRetryableTaskException;

/**
 * Executes one type of task. Implementations are Spring beans; the runner finds them by {@link #type()}
 * and converts the stored JSON into {@code P} (taken from the generic type argument) before calling
 * {@link #handle}. For a single id use its type directly ({@code TaskHandler<Long>},
 * {@code TaskHandler<UUID>}); for several values a small record, e.g.
 * {@code record Payload(Long invoiceId, String locale)}. Ids and small values only.
 *
 * <p><b>Handlers must be idempotent.</b> A task can run more than once: after a crash between the
 * handler's commit and the SUCCEEDED update, after stuck-task recovery, or after a retry that follows
 * a partially successful attempt. Running the same payload twice must not produce a second
 * report, a second email, a second charge...
 *
 * <p>{@link #handle} runs in its own transaction, which commits before the task is marked SUCCEEDED.
 * Throw {@link NonRetryableTaskException} for errors that retrying cannot fix; any other exception
 * is retried with backoff. A payload that cannot be converted to {@code P} is non-retryable too.
 */
public interface TaskHandler<P> {

    TaskType type();

    void handle(P payload);
}
