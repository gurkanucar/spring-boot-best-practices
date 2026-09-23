package com.gucardev.quartzschedulershedlock;

import com.gucardev.quartzschedulershedlock.task.Daily3AmTask;
import com.gucardev.quartzschedulershedlock.task.Every100SecondsTask;
import com.gucardev.quartzschedulershedlock.task.EveryFiveMinutesTask;
import com.gucardev.quartzschedulershedlock.task.EveryMinuteTask;
import com.gucardev.quartzschedulershedlock.task.HourlyTask;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Acceptance criterion: all five example tasks from the spec are Spring-managed beans in
 * the same context, wired together (HourlyTask -&gt; HourlyNotificationTask, every task's
 * {@code @SchedulerLock} against the same LockProvider) without conflict.
 */
@SpringBootTest
@TestPropertySource(properties = "scheduling.enabled=false")
class ScheduledTaskCatalogTest {

    @Autowired
    private EveryMinuteTask everyMinuteTask;

    @Autowired
    private EveryFiveMinutesTask everyFiveMinutesTask;

    @Autowired
    private HourlyTask hourlyTask;

    @Autowired
    private Daily3AmTask daily3AmTask;

    @Autowired
    private Every100SecondsTask every100SecondsTask;

    @Test
    void allFiveExampleTasksAreWiredIntoTheContext() {
        assertThat(everyMinuteTask).isNotNull();
        assertThat(everyFiveMinutesTask).isNotNull();
        assertThat(hourlyTask).isNotNull();
        assertThat(daily3AmTask).isNotNull();
        assertThat(every100SecondsTask).isNotNull();
    }
}
