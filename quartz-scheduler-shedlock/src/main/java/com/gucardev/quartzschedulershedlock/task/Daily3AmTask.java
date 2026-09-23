package com.gucardev.quartzschedulershedlock.task;

import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Fires once a day at 03:00 — a typical overnight batch job scenario. {@code lockAtMostFor}
 * is intentionally generous since batch work can run long.
 */
@Slf4j
@Component
public class Daily3AmTask {

    @Scheduled(cron = "0 0 3 * * ?")
    @SchedulerLock(name = "daily3AmTask", lockAtMostFor = "PT30M", lockAtLeastFor = "PT1M")
    public void run() {
        log.info("Daily3AmTask running its nightly batch work on thread {}", Thread.currentThread().getName());
    }
}
