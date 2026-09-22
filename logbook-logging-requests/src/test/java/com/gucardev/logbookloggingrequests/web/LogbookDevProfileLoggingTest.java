package com.gucardev.logbookloggingrequests.web;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import com.gucardev.logbookloggingrequests.support.LogCapture;
import com.gucardev.logbookloggingrequests.web.LogbookController.Ack;
import com.gucardev.logbookloggingrequests.web.LogbookController.LargePayloadRequest;
import com.gucardev.logbookloggingrequests.web.LogbookController.SensitiveRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;

/** dev profile: logbook.filter.enabled=true, no status restriction (see application-dev.yaml). */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@ActiveProfiles("dev")
class LogbookDevProfileLoggingTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void authorizationHeaderAndSensitiveBodyFieldsAreObfuscatedButOtherFieldsAreNot() {
        var headers = new HttpHeaders();
        headers.set("Authorization", "Bearer super-secret-jwt");
        var request = new HttpEntity<>(
                new SensitiveRequest("demo-user", "p@ssw0rd", "key-12345", "tok-67890"), headers);

        try (var capture = new LogCapture("org.zalando.logbook.Logbook", Level.TRACE)) {
            restTemplate.postForEntity("/api/logbook/sensitive", request, Ack.class);

            String logged = capture.events().getFirst().getFormattedMessage();
            assertThat(logged).contains("Authorization: XXX")
                    .contains("\"password\":\"XXX\"")
                    .contains("\"apiKey\":\"XXX\"")
                    .contains("\"token\":\"XXX\"")
                    .contains("\"username\":\"demo-user\"")
                    .doesNotContain("super-secret-jwt", "p@ssw0rd", "key-12345", "tok-67890");
        }
    }

    @Test
    void bodyLargerThanMaxBodySizeIsTruncatedInTheLoggedRequest() {
        String text = "x".repeat(6_000);

        try (var capture = new LogCapture("org.zalando.logbook.Logbook", Level.TRACE)) {
            restTemplate.postForEntity("/api/logbook/large", new LargePayloadRequest(text), Ack.class);

            String logged = capture.events().getFirst().getFormattedMessage();
            // logback.write.max-body-size = 4096; Logbook appends "..." after truncating.
            String body = logged.substring(logged.lastIndexOf("\n\n") + 2);
            assertThat(body).endsWith("...").hasSize(4_096 + 3);
        }
    }
}
