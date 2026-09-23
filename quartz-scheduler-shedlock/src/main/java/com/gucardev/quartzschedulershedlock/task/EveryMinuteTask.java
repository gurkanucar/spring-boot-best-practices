package com.gucardev.quartzschedulershedlock.task;

import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.DoubleSupplier;

/**
 * Fires every minute (cron {@code 0 * * * * ?}). Intentionally flaky — fails about 30%
 * of the time — so the global scheduled-task error handler (see SchedulingConfig) has
 * something real to catch and log; see the README section on error handling.
 *
 * <p>{@code @SchedulerLock} sits on the same method as {@code @Scheduled}, not on a
 * separate delegate — Spring always invokes {@code @Scheduled} methods through the
 * bean's proxy (never via self-invocation), so both annotations' AOP advice apply
 * correctly here.
 */
@Slf4j
@Component
public class EveryMinuteTask {

    private static final double FAILURE_PROBABILITY = 0.3;

    private final DoubleSupplier randomSource;
    private final AtomicInteger executions = new AtomicInteger();

    public EveryMinuteTask() {
        this(() -> ThreadLocalRandom.current().nextDouble());
    }

    /** Package-private seam so tests can force the success/failure branch deterministically. */
    EveryMinuteTask(DoubleSupplier randomSource) {
        this.randomSource = randomSource;
    }

    @Scheduled(cron = "0 * * * * ?")
    @SchedulerLock(name = "everyMinuteTask", lockAtMostFor = "PT55S", lockAtLeastFor = "PT10S")
    public void run() {
        executions.incrementAndGet();
        log.info("EveryMinuteTask running on thread {}", Thread.currentThread().getName());
        if (randomSource.getAsDouble() < FAILURE_PROBABILITY) {
            throw new IllegalStateException(
                    "Simulated failure in EveryMinuteTask (~30% chance, for demonstrating error handling)");
        }
        log.info("EveryMinuteTask completed successfully");
    }

    /** How many times the locked method body actually ran on this instance — test-only observability. */
    public int executionCount() {
        return executions.get();
    }

    public void resetExecutionCount() {
        executions.set(0);
    }
}
