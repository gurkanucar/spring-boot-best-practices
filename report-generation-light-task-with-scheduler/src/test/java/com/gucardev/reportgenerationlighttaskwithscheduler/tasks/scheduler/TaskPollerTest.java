package com.gucardev.reportgenerationlighttaskwithscheduler.tasks.scheduler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.gucardev.reportgenerationlighttaskwithscheduler.tasks.dto.ClaimedTask;
import com.gucardev.reportgenerationlighttaskwithscheduler.tasks.entity.TaskType;
import com.gucardev.reportgenerationlighttaskwithscheduler.tasks.service.TaskRunner;
import com.gucardev.reportgenerationlighttaskwithscheduler.tasks.service.TaskService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

class TaskPollerTest {

    @Test
    void rejectedSubmissionReturnsEveryClaimedTaskWithoutRunningIt() {
        var service = mock(TaskService.class);
        var runner = mock(TaskRunner.class);
        var executor = mock(ThreadPoolTaskExecutor.class);
        when(executor.getMaxPoolSize()).thenReturn(2);
        var first = new ClaimedTask(UUID.randomUUID(), TaskType.EMAIL_SEND, "null", 1, 5);
        var second = new ClaimedTask(UUID.randomUUID(), TaskType.EMAIL_SEND, "null", 1, 5);
        when(service.claim(2)).thenReturn(List.of(first, second));
        doThrow(new TaskRejectedException("Executor is shutting down")).when(executor).execute(any(Runnable.class));

        new TaskPoller(service, runner, executor).poll();

        verify(runner).putBack(first);
        verify(runner).putBack(second);
        verify(runner, never()).run(any());
    }
}
