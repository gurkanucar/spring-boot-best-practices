package com.gucardev.quartzschedulershedlock.task;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import com.gucardev.quartzschedulershedlock.config.AsyncConfig;
import com.gucardev.quartzschedulershedlock.support.LogCapture;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Proves the real {@code @Async} proxy + {@code AsyncUncaughtExceptionHandler} wiring
 * end-to-end — calling {@link HourlyNotificationTask#send(String)} through the actual
 * Spring-managed bean, not invoking the handler by hand. A test that only calls the
 * handler directly would still pass even if {@code @EnableAsync} were removed or the
 * method ran synchronously, so it wouldn't prove anything was actually wired.
 *
 * <p>Lives in the {@code task} package (not {@code config}, where {@link AsyncConfig}
 * itself lives) because it needs {@link HourlyNotificationTask}'s package-private
 * deterministic-random constructor.
 */
@SpringBootTest
class AsyncUncaughtExceptionHandlerTest {

    private static final String MARKER = "async-uncaught-exception-handler-test-marker";

    @TestConfiguration
    static class AlwaysFailingNotificationTaskConfig {

        @Bean
        @Primary
        HourlyNotificationTask alwaysFailingHourlyNotificationTask() {
            return new HourlyNotificationTask(() -> 0.0);
        }
    }

    @Autowired
    private HourlyNotificationTask hourlyNotificationTask;

    @Test
    void asyncFailureIsCaughtByTheHandlerAndLoggedWithItsStackTrace() throws InterruptedException {
        try (LogCapture logCapture = new LogCapture(AsyncConfig.class, Level.ERROR)) {
            // @Async void returns immediately without throwing, regardless of what the
            // real method body does on its own thread — proves the call went through
            // the async proxy rather than running (and throwing) synchronously here.
            assertThatCode(() -> hourlyNotificationTask.send(MARKER)).doesNotThrowAnyException();

            ILoggingEvent event = awaitLogEvent(logCapture);

            assertThat(event.getFormattedMessage()).contains("HourlyNotificationTask", "send");
            assertThat(event.getThrowableProxy()).isNotNull();
        }
    }

    private ILoggingEvent awaitLogEvent(LogCapture logCapture) throws InterruptedException {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(5));
        while (Instant.now().isBefore(deadline)) {
            if (!logCapture.events().isEmpty()) {
                return logCapture.events().get(0);
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Expected an async failure log event within 5 seconds but none was captured");
    }
}
