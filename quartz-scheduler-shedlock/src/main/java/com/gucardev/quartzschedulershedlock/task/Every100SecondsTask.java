package com.gucardev.quartzschedulershedlock.task;

import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Fires every 100 seconds, forever — a non-cron, interval-based schedule (Spring's
 * {@code fixedRate}, in milliseconds). Useful when a fixed cron minute grid doesn't fit
 * the desired cadence.
 *
 * <p>{@code fixedRate} counts from each JVM's own startup time, so two instances started
 * at different times fire at unrelated offsets — unlike the cron-based tasks in this
 * catalog, which all fire at the same wall-clock instant everywhere. For an interval
 * schedule, {@code lockAtLeastFor} has to be close to the full period (here, 90s of a
 * 100s period) for cross-instance dedupe to actually work; a short one (e.g. 10s) would
 * let every instance run once per period regardless of the others.
 */
@Slf4j
@Component
public class Every100SecondsTask {

    @Scheduled(fixedRate = 100_000)
    @SchedulerLock(name = "every100SecondsTask", lockAtMostFor = "PT95S", lockAtLeastFor = "PT90S")
    public void run() {
        log.info("Every100SecondsTask running on thread {}", Thread.currentThread().getName());
    }
}
