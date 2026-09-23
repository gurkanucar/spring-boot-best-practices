package com.gucardev.quartzschedulershedlock.task;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadLocalRandom;
import java.util.function.DoubleSupplier;

import static com.gucardev.quartzschedulershedlock.config.AsyncConfig.QUARTZ_ASYNC_EXECUTOR;

/**
 * Fire-and-forget side effect triggered by {@link HourlyTask}, deliberately called
 * outside the lock (see {@link HourlyTask#run()}). Fails ~20% of the time to exercise
 * the {@code AsyncUncaughtExceptionHandler} configured in AsyncConfig — since this
 * method returns {@code void}, an exception here cannot propagate back to any caller.
 */
@Slf4j
@Component
public class HourlyNotificationTask {

    private static final double FAILURE_PROBABILITY = 0.2;

    private final DoubleSupplier randomSource;

    public HourlyNotificationTask() {
        this(() -> ThreadLocalRandom.current().nextDouble());
    }

    /** Package-private seam so tests can force the success/failure branch deterministically. */
    HourlyNotificationTask(DoubleSupplier randomSource) {
        this.randomSource = randomSource;
    }

    @Async(QUARTZ_ASYNC_EXECUTOR)
    public void send(String message) {
        log.info("Sending notification asynchronously: {}", message);
        if (randomSource.getAsDouble() < FAILURE_PROBABILITY) {
            throw new IllegalStateException("Simulated notification delivery failure (~20% chance)");
        }
        log.info("Notification sent successfully");
    }
}
