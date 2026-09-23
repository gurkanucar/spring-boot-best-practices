package com.gucardev.quartzschedulershedlock.task;

import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Fires every 5 minutes, on the 0/5/10/15... minute marks. Plain, always-successful example. */
@Slf4j
@Component
public class EveryFiveMinutesTask {

    @Scheduled(cron = "0 */5 * * * ?")
    @SchedulerLock(name = "everyFiveMinutesTask", lockAtMostFor = "PT4M50S", lockAtLeastFor = "PT30S")
    public void run() {
        log.info("EveryFiveMinutesTask running on thread {}", Thread.currentThread().getName());
    }
}
