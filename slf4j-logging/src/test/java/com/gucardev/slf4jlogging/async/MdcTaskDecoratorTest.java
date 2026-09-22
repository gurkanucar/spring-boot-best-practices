package com.gucardev.slf4jlogging.async;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

class MdcTaskDecoratorTest {

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void capturesAtSubmissionAndRestoresWorkerContextEvenOnFailure() throws Exception {
        try (var executor = Executors.newSingleThreadExecutor()) {
            executor.submit(() -> MDC.put("worker", "previous")).get(5, TimeUnit.SECONDS);
            MDC.put("requestId", "first-request");
            Runnable task = new MdcTaskDecorator().decorate(() -> {
                assertThat(MDC.get("requestId")).isEqualTo("first-request");
                assertThat(MDC.get("worker")).isNull();
                throw new IllegalStateException("simulated worker failure");
            });
            MDC.put("requestId", "second-request");
            var future = executor.submit(task);
            assertThatThrownBy(() -> future.get(5, TimeUnit.SECONDS)).hasCauseInstanceOf(IllegalStateException.class);
            assertThat(executor.submit(MDC::getCopyOfContextMap).get(5, TimeUnit.SECONDS))
                    .containsOnlyKeys("worker").containsEntry("worker", "previous");
            assertThat(MDC.get("requestId")).isEqualTo("second-request");
        }
    }

    @Test
    void taskWithoutCallerContextDoesNotInheritStaleWorkerValues() throws Exception {
        Runnable task = new MdcTaskDecorator().decorate(() -> assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty());
        try (var executor = Executors.newSingleThreadExecutor()) {
            executor.submit(() -> MDC.put("requestId", "stale-worker")).get(5, TimeUnit.SECONDS);
            executor.submit(task).get(5, TimeUnit.SECONDS);
            assertThat(executor.submit(() -> MDC.get("requestId")).get(5, TimeUnit.SECONDS)).isEqualTo("stale-worker");
        }
    }
}
