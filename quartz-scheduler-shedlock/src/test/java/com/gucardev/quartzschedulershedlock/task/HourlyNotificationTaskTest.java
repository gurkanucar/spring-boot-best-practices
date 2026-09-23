package com.gucardev.quartzschedulershedlock.task;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HourlyNotificationTaskTest {

    @Test
    void throwsWhenRandomValueIsBelowFailureProbability() {
        HourlyNotificationTask task = new HourlyNotificationTask(() -> 0.1);

        assertThatThrownBy(() -> task.send("hello"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Simulated notification delivery failure");
    }

    @Test
    void doesNotThrowWhenRandomValueIsAboveFailureProbability() {
        HourlyNotificationTask task = new HourlyNotificationTask(() -> 0.9);

        assertThatNoException().isThrownBy(() -> task.send("hello"));
    }
}
