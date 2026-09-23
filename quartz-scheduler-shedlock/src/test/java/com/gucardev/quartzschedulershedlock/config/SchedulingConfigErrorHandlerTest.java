package com.gucardev.quartzschedulershedlock.config;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import com.gucardev.quartzschedulershedlock.support.LogCapture;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.scheduling.TaskScheduler;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the real {@link TaskScheduler} bean's {@code ErrorHandler} (installed in
 * {@link SchedulingConfig}) actually catches and logs a failing scheduled task — this
 * was previously the one error path in this module with no test at all.
 *
 * <p>Schedules a real one-off task with a uniquely marked exception through the actual
 * production {@code TaskScheduler}, rather than constructing/invoking an ErrorHandler by
 * hand — this is what proves the wiring, not just the handler's own logic in isolation.
 * The marker keeps the assertion robust even if an unrelated real {@code @Scheduled}
 * task (scheduling stays enabled here, since disabling it would remove the very
 * TaskScheduler bean under test) also happens to log through the same logger.
 */
@SpringBootTest
class SchedulingConfigErrorHandlerTest {

    private static final String MARKER = "scheduling-config-error-handler-test-marker";

    @Autowired
    private TaskScheduler taskScheduler;

    @Test
    void aThrowingScheduledTaskIsCaughtAndLoggedByTheCustomErrorHandler() throws InterruptedException {
        try (LogCapture logCapture = new LogCapture(SchedulingConfig.class, Level.ERROR)) {
            taskScheduler.schedule(() -> {
                throw new IllegalStateException(MARKER);
            }, Instant.now());

            ILoggingEvent event = awaitMarkerEvent(logCapture);

            assertThat(event.getFormattedMessage()).isEqualTo("A @Scheduled task failed");
            assertThat(event.getThrowableProxy()).isNotNull();
            assertThat(event.getThrowableProxy().getMessage()).isEqualTo(MARKER);
        }
    }

    private ILoggingEvent awaitMarkerEvent(LogCapture logCapture) throws InterruptedException {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(5));
        while (Instant.now().isBefore(deadline)) {
            var match = logCapture.events().stream()
                    .filter(event -> event.getThrowableProxy() != null
                            && MARKER.equals(event.getThrowableProxy().getMessage()))
                    .findFirst();
            if (match.isPresent()) {
                return match.get();
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Expected the marker exception to be logged within 5 seconds but it wasn't");
    }
}
