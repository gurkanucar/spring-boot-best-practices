package com.gucardev.reportgenerationlighttaskwithscheduler.tasks;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class TaskRunnerTest {

    @Test
    void backoffGrowsFourfoldUpToOneHour() {
        assertThat(IntStream.rangeClosed(1, 6).mapToObj(TaskRunner::backoff).toList()).containsExactly(
                Duration.ofSeconds(30), Duration.ofMinutes(2), Duration.ofMinutes(8), Duration.ofMinutes(32),
                Duration.ofHours(1), Duration.ofHours(1));
    }
}
