package com.gucardev.quartzschedulershedlock.task;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Fires hourly, on the hour. The main work is fully synchronous and covered by
 * {@code @SchedulerLock} — only one instance ever runs it. After it finishes, it fires a
 * fire-and-forget notification via {@link HourlyNotificationTask} OUTSIDE the lock's
 * scope.
 *
 * <p>This is a deliberate teaching point, not an oversight: {@code @SchedulerLock} only
 * holds the lock for as long as this method's call stack is on it. Because
 * {@link HourlyNotificationTask#send(String)} is {@code @Async}, calling it here returns
 * immediately and the lock is released right after — the notification itself then runs
 * unlocked. That is safe for a best-effort, idempotent side effect like a notification,
 * but would be a bug for work that must not run twice: in that case either call the async
 * method and block on its {@code CompletableFuture} before this method returns, or extend
 * {@code lockAtLeastFor} to comfortably cover the async work's expected duration. See the
 * README section "Async work and lock scope" for the full explanation.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HourlyTask {

    private final HourlyNotificationTask hourlyNotificationTask;

    @Scheduled(cron = "0 0 * * * ?")
    @SchedulerLock(name = "hourlyTask", lockAtMostFor = "PT10M", lockAtLeastFor = "PT30S")
    public void run() {
        log.info("HourlyTask running its synchronous, locked work on thread {}", Thread.currentThread().getName());
        hourlyNotificationTask.send("Hourly job completed at " + Instant.now());
    }
}
