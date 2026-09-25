package com.gucardev.reportgenerationlighttaskwithscheduler.tasks.scheduler;

import com.gucardev.reportgenerationlighttaskwithscheduler.tasks.config.TaskProperties;
import com.gucardev.reportgenerationlighttaskwithscheduler.tasks.repository.BackgroundTaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * An instance that crashes (or is killed during shutdown) leaves its tasks RUNNING forever.
 * This job retries them, or marks them DEAD when the execution budget is exhausted.
 * The conditional SQL update is safe when recovery runs on several instances.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class StuckTaskRecovery {

    private final BackgroundTaskRepository repository;
    private final TaskProperties properties;

    @Scheduled(fixedDelayString = "1m", initialDelayString = "1m")
    @SchedulerLock(name = "stuckTaskRecovery", lockAtMostFor = "50s")
    public void recover() {
        recoverStuckTasks();
    }

    /** One recovery pass. Returns the number of tasks retried or marked DEAD. */
    public int recoverStuckTasks() {
        int recovered = repository.recoverStuck(properties.stuckAfter().toSeconds());
        if (recovered > 0) {
            log.warn("Recovered {} tasks RUNNING for more than {}", recovered, properties.stuckAfter());
        }
        return recovered;
    }
}
