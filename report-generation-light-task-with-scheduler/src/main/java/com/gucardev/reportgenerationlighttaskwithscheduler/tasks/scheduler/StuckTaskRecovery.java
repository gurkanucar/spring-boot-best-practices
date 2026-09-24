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
 * This job hands them back to PENDING. Attempts stay unchanged: the stall was not the task's fault.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class StuckTaskRecovery {

    private final BackgroundTaskRepository repository;
    private final TaskProperties properties;

    @Scheduled(fixedDelayString = "1m", initialDelayString = "1m")
    @SchedulerLock(name = "stuckTaskRecovery", lockAtMostFor = "50s", lockAtLeastFor = "10s")
    public void recover() {
        recoverStuckTasks();
    }

    /** One recovery pass without the scheduler lock. Returns the number of tasks handed back. */
    public int recoverStuckTasks() {
        int recovered = repository.recoverStuck(properties.stuckAfter().toSeconds());
        if (recovered > 0) {
            log.warn("Recovered {} tasks RUNNING for more than {}", recovered, properties.stuckAfter());
        }
        return recovered;
    }
}
