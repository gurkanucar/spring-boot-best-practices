package com.gucardev.slf4jlogging.examples;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import com.gucardev.slf4jlogging.support.LogCapture;
import com.gucardev.slf4jlogging.web.LoggingController.SensitiveRequest;
import org.junit.jupiter.api.Test;

class SafeLoggingExamplesTest {

    @Test
    void lineBreaksAndControlCharactersCannotCreateFakeTextLogLines() {
        try (var capture = new LogCapture(SafeLoggingExamples.class, Level.INFO)) {
            new SafeLoggingExamples().userSuppliedText("hello\r\nERROR forged\t\u2028next");
            assertThat(capture.events().getFirst().getFormattedMessage())
                    .isEqualTo("User supplied note=hello__ERROR forged__next");
        }
        assertThat(SafeLoggingExamples.oneLine("x".repeat(1_000))).hasSize(120);
    }

    @Test
    void emailIsMaskedAndSensitiveDtoDoesNotExposeCredentialsInToString() {
        assertThat(SafeLoggingExamples.maskEmail("private-person@example.com")).isEqualTo("***@example.com");
        assertThat(new SensitiveRequest("private-person@example.com", "secret-password", "secret-token").toString())
                .doesNotContain("private-person", "secret-password", "secret-token");
    }
}
