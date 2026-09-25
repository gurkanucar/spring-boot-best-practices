package com.gucardev.reportgenerationlighttaskwithscheduler.tasks.scheduler;

import com.gucardev.reportgenerationlighttaskwithscheduler.tasks.dto.ClaimedTask;
import com.gucardev.reportgenerationlighttaskwithscheduler.tasks.service.TaskRunner;
import com.gucardev.reportgenerationlighttaskwithscheduler.tasks.service.TaskService;
import lombok.RequiredArgsConstructor;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

/** ShedLock serializes dispatch; the Spring executor owns worker capacity and shutdown. */
@Component
@RequiredArgsConstructor
public class TaskPoller {

    private final TaskService taskService;
    private final TaskRunner runner;
    private final ThreadPoolTaskExecutor taskWorkerExecutor;

    // The lock covers this short dispatch, not the 5-10 minute handler execution.
    @Scheduled(fixedDelayString = "${tasks.poll-interval:2s}")
    @SchedulerLock(name = "taskPoller", lockAtMostFor = "30s")
    public void poll() {
        int free = taskWorkerExecutor.getMaxPoolSize() - taskWorkerExecutor.getActiveCount();
        if (free <= 0) {
            return;
        }
        for (ClaimedTask task : taskService.claim(free)) {
            try {
                taskWorkerExecutor.execute(() -> runner.run(task));
            } catch (TaskRejectedException e) {
                // activeCount is a hint, not a reservation. Also handles executor shutdown.
                runner.putBack(task);
            }
        }
    }
}
