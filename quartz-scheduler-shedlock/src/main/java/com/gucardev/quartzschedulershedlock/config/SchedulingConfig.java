package com.gucardev.quartzschedulershedlock.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * Enables {@code @Scheduled} and installs a custom {@link TaskScheduler} whose
 * {@code ErrorHandler} logs every uncaught exception from a scheduled method.
 *
 * <p>Spring's own default already logs every repeating-task failure at ERROR with the
 * stack trace (via {@code TaskUtils.LOG_AND_SUPPRESS_ERROR_HANDLER}) — this custom
 * handler isn't here to prevent a silent failure that wasn't going to happen anyway.
 * It exists as the single, explicit place to put your own policy instead of relying on
 * an implicit default: a project-specific log format, an added metric, an alert hook,
 * or anything else you'd want to happen exactly once per scheduled-task failure,
 * regardless of which task threw.
 *
 * <p>Gated by {@code scheduling.enabled} (default {@code true}) so tests that need to
 * assert on lock/task state deterministically can turn real background firing off with
 * {@code @TestPropertySource(properties = "scheduling.enabled=false")}, the same way
 * cron-driven tests would otherwise risk colliding with a real fire mid-assertion.
 */
@Slf4j
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {

    @Bean
    public TaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(5);
        scheduler.setThreadNamePrefix("scheduled-task-");
        scheduler.setErrorHandler(throwable -> log.error("A @Scheduled task failed", throwable));
        return scheduler;
    }
}
