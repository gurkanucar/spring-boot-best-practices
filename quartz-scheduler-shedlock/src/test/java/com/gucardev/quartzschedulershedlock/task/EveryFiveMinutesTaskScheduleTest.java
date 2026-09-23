package com.gucardev.quartzschedulershedlock.task;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

import static org.assertj.core.api.Assertions.assertThat;

class EveryFiveMinutesTaskScheduleTest {

    @Test
    void runIsScheduledWithTheExpectedCronExpression() throws NoSuchMethodException {
        Scheduled scheduled = EveryFiveMinutesTask.class.getMethod("run").getAnnotation(Scheduled.class);

        assertThat(scheduled).isNotNull();
        assertThat(scheduled.cron()).isEqualTo("0 */5 * * * ?");
    }
}
