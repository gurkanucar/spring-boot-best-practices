package com.gucardev.slf4jlogging.examples;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import com.gucardev.slf4jlogging.support.LogCapture;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class LoggingExamplesTest {

    private final LoggingExamples examples = new LoggingExamples();

    @ParameterizedTest
    @CsvSource({"TRACE,5", "DEBUG,4", "INFO,3", "WARN,2", "ERROR,1", "OFF,0"})
    void effectiveLevelFiltersLowerSeverityEvents(String threshold, int count) {
        try (var capture = new LogCapture(LoggingExamples.class, Level.toLevel(threshold))) {
            examples.levels(42);
            assertThat(capture.events()).hasSize(count);
            if (count > 0) {
                assertThat(capture.events().getFirst().getLevel()).isEqualTo(Level.toLevel(threshold));
                assertThat(capture.events().getLast().getLevel()).isEqualTo(Level.ERROR);
            }
        }
    }

    @Test
    void placeholdersRenderValuesWithoutStringConcatenation() {
        try (var capture = new LogCapture(LoggingExamples.class, Level.INFO)) {
            examples.parameterized(42);
            assertThat(capture.events().getFirst().getMessage()).contains("orderId={}");
            assertThat(capture.events().getFirst().getFormattedMessage())
                    .isEqualTo("Order selected orderId=42 itemCount=3");
        }
    }

    @Test
    void neitherGuardNorSupplierComputesDetailsWhenDebugIsDisabled() {
        AtomicInteger calculations = new AtomicInteger();
        try (var capture = new LogCapture(LoggingExamples.class, Level.INFO)) {
            examples.lazy(42, () -> "calculation-" + calculations.incrementAndGet());
            assertThat(calculations).hasValue(0);
            assertThat(capture.events()).isEmpty();
        }
        try (var capture = new LogCapture(LoggingExamples.class, Level.DEBUG)) {
            examples.lazy(42, () -> "calculation-" + calculations.incrementAndGet());
            assertThat(calculations).hasValue(2);
            assertThat(capture.events()).hasSize(2);
        }
    }

    @Test
    void fluentApiRetainsTypedKeyValuePairs() {
        try (var capture = new LogCapture(LoggingExamples.class, Level.INFO)) {
            examples.structured(42);
            assertThat(capture.events().getFirst().getKeyValuePairs())
                    .anySatisfy(pair -> {
                        assertThat(pair.key).isEqualTo("orderId");
                        assertThat(pair.value).isEqualTo(42L);
                    });
        }
    }

    @Test
    void auditHasItsOwnLoggerCategoryAndMarker() {
        try (var capture = new LogCapture("audit", Level.INFO)) {
            new AuditExamples().orderProcessed(42);
            var event = capture.events().getFirst();
            assertThat(event.getLoggerName()).isEqualTo("audit");
            assertThat(event.getMarkerList()).extracting(marker -> marker.getName()).contains("AUDIT");
        }
    }
}
