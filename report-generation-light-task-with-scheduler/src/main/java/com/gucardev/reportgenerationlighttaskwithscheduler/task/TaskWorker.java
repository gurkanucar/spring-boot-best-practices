package com.gucardev.reportgenerationlighttaskwithscheduler.task;

import com.gucardev.reportgenerationlighttaskwithscheduler.report.ReportTasks;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class TaskWorker {

    private static final Duration MAX_BACKOFF = Duration.ofHours(1);
    private static final int MAX_ERROR_LENGTH = 2000;

    private final BackgroundTaskRepository tasks;
    private final ReportTasks reportTasks;
    private final ThreadPoolTaskExecutor executor;
    private final Duration firstRetryDelay;
    private final Duration stuckAfter;

    public TaskWorker(BackgroundTaskRepository tasks, ReportTasks reportTasks, ThreadPoolTaskExecutor taskWorkerExecutor,
                      @Value("${tasks.first-retry-delay:30s}") Duration firstRetryDelay,
                      @Value("${tasks.stuck-after:20m}") Duration stuckAfter) {
        this.tasks = tasks;
        this.reportTasks = reportTasks;
        this.executor = taskWorkerExecutor;
        this.firstRetryDelay = firstRetryDelay;
        this.stuckAfter = stuckAfter;
    }

    /**
     * Claims only as many tasks as there are free workers; the rest stay PENDING in the database.
     * ShedLock lets one instance dispatch at a time. The lock covers this short claim, not the task run.
     */
    @Scheduled(fixedDelayString = "${tasks.poll-interval:2s}")
    @SchedulerLock(name = "taskPoller", lockAtMostFor = "30s")
    public void poll() {
        int free = executor.getMaxPoolSize() - executor.getActiveCount();
        if (free <= 0) {
            return;
        }
        for (BackgroundTask task : tasks.claim(free)) {
            try {
                executor.execute(() -> run(task));
            } catch (TaskRejectedException e) {
                // activeCount is a hint, not a reservation; this also happens during shutdown.
                tasks.putBack(task.getId(), task.getAttempts());
            }
        }
    }

    @Scheduled(fixedDelayString = "1m", initialDelayString = "1m")
    @SchedulerLock(name = "stuckTaskRecovery", lockAtMostFor = "50s")
    public void recoverStuck() {
        int recovered = tasks.recoverStuck(stuckAfter.toMillis() / 1000.0);
        if (recovered > 0) {
            log.warn("Recovered {} tasks RUNNING for more than {}", recovered, stuckAfter);
        }
    }

    // No transaction around the handler: it owns its short transactions.
    private void run(BackgroundTask task) {
        try {
            reportTasks.run(task.getType(), task.getPayload());
        } catch (Exception e) {
            Duration delay = backoff(task.getAttempts());
            tasks.markFailed(task.getId(), task.getAttempts(), delay.toMillis() / 1000.0, summarize(e));
            if (task.getAttempts() >= task.getMaxAttempts()) {
                log.error("Task {} {} is DEAD after attempt {}", task.getType(), task.getId(), task.getAttempts(), e);
            } else {
                log.warn("Task {} {} failed (attempt {}), retry in {}", task.getType(), task.getId(),
                        task.getAttempts(), delay, e);
            }
            return;
        }
        tasks.markSucceeded(task.getId(), task.getAttempts());
    }

    /** 30s, 2m, 8m, 32m, then 1h (with the default first delay). */
    private Duration backoff(int attempts) {
        Duration delay = firstRetryDelay.multipliedBy(1L << (2 * Math.min(attempts - 1, 10)));
        return delay.compareTo(MAX_BACKOFF) > 0 ? MAX_BACKOFF : delay;
    }

    private static String summarize(Throwable error) {
        StringBuilder summary = new StringBuilder(error.toString());
        Throwable cause = error.getCause();
        for (int depth = 0; cause != null && depth < 5; cause = cause.getCause(), depth++) {
            summary.append("\nCaused by: ").append(cause);
        }
        return summary.length() <= MAX_ERROR_LENGTH ? summary.toString() : summary.substring(0, MAX_ERROR_LENGTH);
    }
}
