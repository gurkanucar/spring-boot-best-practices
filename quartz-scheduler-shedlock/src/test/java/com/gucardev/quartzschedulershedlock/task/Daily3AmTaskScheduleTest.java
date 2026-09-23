package com.gucardev.quartzschedulershedlock.task;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

import static org.assertj.core.api.Assertions.assertThat;

class Daily3AmTaskScheduleTest {

    @Test
    void runIsScheduledWithTheExpectedCronExpression() throws NoSuchMethodException {
        Scheduled scheduled = Daily3AmTask.class.getMethod("run").getAnnotation(Scheduled.class);

        assertThat(scheduled).isNotNull();
        assertThat(scheduled.cron()).isEqualTo("0 0 3 * * ?");
    }
}
