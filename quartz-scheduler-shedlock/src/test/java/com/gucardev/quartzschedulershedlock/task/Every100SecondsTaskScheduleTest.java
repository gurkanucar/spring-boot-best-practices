package com.gucardev.quartzschedulershedlock.task;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

import static org.assertj.core.api.Assertions.assertThat;

class Every100SecondsTaskScheduleTest {

    @Test
    void runIsScheduledAtAFixedRateOf100Seconds() throws NoSuchMethodException {
        Scheduled scheduled = Every100SecondsTask.class.getMethod("run").getAnnotation(Scheduled.class);

        assertThat(scheduled).isNotNull();
        assertThat(scheduled.fixedRate()).isEqualTo(100_000L);
    }

    /**
     * fixedRate counts from each JVM's own startup time, so two instances started at
     * different times fire at unrelated offsets — lockAtLeastFor is the ONLY thing that
     * can make this dedupe across instances, and it only works if it's close to the
     * full period. A short lockAtLeastFor (e.g. 10s against a 100s period) would let
     * every instance run once per period regardless of the others.
     */
    @Test
    void lockAtLeastForIsCloseToTheFullPeriodSoDriftedInstancesStillDedupe() throws NoSuchMethodException {
        SchedulerLock schedulerLock = Every100SecondsTask.class.getMethod("run").getAnnotation(SchedulerLock.class);

        assertThat(schedulerLock).isNotNull();
        assertThat(schedulerLock.lockAtLeastFor()).isEqualTo("PT90S");
    }
}
