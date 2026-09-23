package com.gucardev.quartzschedulershedlock.task;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EveryMinuteTaskTest {

    @Test
    void throwsWhenRandomValueIsBelowFailureProbability() {
        EveryMinuteTask task = new EveryMinuteTask(() -> 0.1);

        assertThatThrownBy(task::run)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Simulated failure");
        assertThat(task.executionCount()).isEqualTo(1);
    }

    @Test
    void doesNotThrowWhenRandomValueIsAboveFailureProbability() {
        EveryMinuteTask task = new EveryMinuteTask(() -> 0.9);

        assertThatNoException().isThrownBy(task::run);
        assertThat(task.executionCount()).isEqualTo(1);
    }

    @Test
    void resetExecutionCountSetsItBackToZero() {
        EveryMinuteTask task = new EveryMinuteTask(() -> 0.9);
        task.run();

        task.resetExecutionCount();

        assertThat(task.executionCount()).isZero();
    }
}
