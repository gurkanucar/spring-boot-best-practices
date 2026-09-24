package com.gucardev.reportgenerationlighttaskwithscheduler.tasks.scheduler;

import com.gucardev.reportgenerationlighttaskwithscheduler.tasks.config.TaskProperties;
import com.gucardev.reportgenerationlighttaskwithscheduler.tasks.dto.ClaimedTask;
import com.gucardev.reportgenerationlighttaskwithscheduler.tasks.service.TaskRunner;
import com.gucardev.reportgenerationlighttaskwithscheduler.tasks.service.TaskService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.context.SmartLifecycle;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

/**
 * Claims due tasks and hands them to the worker pool. It never runs a task itself.
 *
 * <p>As a {@link SmartLifecycle} it is stopped early during shutdown: it stops claiming, then waits
 * up to {@code tasks.shutdown-wait} for running tasks. Tasks still running after that stay RUNNING
 * and are rescheduled by {@link StuckTaskRecovery}.
 */
@Component
@Slf4j
public class TaskPoller implements SmartLifecycle {

    private final TaskService taskService;
    private final TaskRunner runner;
    private final ThreadPoolTaskExecutor executor;
    private final TaskProperties properties;

    // One permit per worker thread. Taken at submit, returned when the task finishes.
    private final Semaphore freeWorkers;
    private volatile boolean running;

    public TaskPoller(TaskService taskService, TaskRunner runner, ThreadPoolTaskExecutor taskWorkerExecutor,
                      TaskProperties properties) {
        this.taskService = taskService;
        this.runner = runner;
        this.executor = taskWorkerExecutor;
        this.properties = properties;
        this.freeWorkers = new Semaphore(properties.workers());
    }

    // The ShedLock lock only covers claiming and submitting (milliseconds), not the task execution.
    @Scheduled(fixedDelayString = "${tasks.poll-interval:2s}")
    @SchedulerLock(name = "taskPoller", lockAtMostFor = "30s", lockAtLeastFor = "1s")
    public void poll() {
        if (!running) {
            return;
        }
        int free = freeWorkers.availablePermits();
        if (free == 0) {
            return;
        }
        for (ClaimedTask task : taskService.claim(free)) {
            submit(task);
        }
    }

    private void submit(ClaimedTask task) {
        // Only the poller takes permits, so the permits counted above are still there.
        if (!freeWorkers.tryAcquire()) {
            runner.putBack(task);
            return;
        }
        try {
            executor.execute(() -> {
                try {
                    runner.run(task);
                } finally {
                    freeWorkers.release();
                }
            });
        } catch (TaskRejectedException e) { // the executor is shutting down
            freeWorkers.release();
            runner.putBack(task);
        }
    }

    @Override
    public void start() {
        running = true;
    }

    @Override
    public void stop() {
        running = false;
        int workers = properties.workers();
        try {
            if (freeWorkers.tryAcquire(workers, properties.shutdownWait().toMillis(), TimeUnit.MILLISECONDS)) {
                freeWorkers.release(workers);
                log.info("Task poller stopped, no tasks running");
            } else {
                log.warn("{} tasks still running after {}; stuck task recovery will reschedule them",
                        workers - freeWorkers.availablePermits(), properties.shutdownWait());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }
}
