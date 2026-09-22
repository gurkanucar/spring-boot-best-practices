package com.gucardev.logbookloggingrequests.web;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import com.gucardev.logbookloggingrequests.support.LogCapture;
import com.gucardev.logbookloggingrequests.web.LogbookController.Ack;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.test.context.ActiveProfiles;

/** prod profile: logbook.strategy=status-at-least, logbook.minimum-status=500 (application-prod.yaml). */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@ActiveProfiles("prod")
class LogbookProdProfileLoggingTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void onlyResponsesAtOrAboveTheMinimumStatusAreLogged() {
        try (var capture = new LogCapture("org.zalando.logbook.Logbook", Level.TRACE)) {
            restTemplate.getForEntity("/api/logbook/status/201", Ack.class);
            restTemplate.getForEntity("/api/logbook/status/404", Ack.class);
            assertThat(capture.events()).isEmpty();

            restTemplate.getForEntity("/api/logbook/status/500", Ack.class);
            restTemplate.getForEntity("/api/logbook/status/503", Ack.class);
            // one Incoming Request + one Outgoing Response event per logged call
            assertThat(capture.events()).hasSize(4);
        }
    }
}
