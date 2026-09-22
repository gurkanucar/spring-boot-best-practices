package com.gucardev.logbookloggingrequests.web;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import com.gucardev.logbookloggingrequests.support.LogCapture;
import com.gucardev.logbookloggingrequests.web.LogbookController.Ack;
import com.gucardev.logbookloggingrequests.web.LogbookController.EchoRequest;
import com.gucardev.logbookloggingrequests.web.LogbookController.EchoResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.ResponseEntity;

/** No profile active: matches application.yaml defaults (logbook.filter.enabled=false). */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class LogbookControllerTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void echoReturnsTheSameMessageAndItsLength() {
        var response = restTemplate.postForEntity("/api/logbook/echo",
                new EchoRequest("hello logbook"), EchoResponse.class);
        assertThat(response.getBody()).isEqualTo(new EchoResponse("hello logbook", 13));
    }

    @Test
    void statusEndpointReturnsExactlyTheRequestedCode() {
        ResponseEntity<Ack> response = restTemplate.getForEntity("/api/logbook/status/201", Ack.class);
        assertThat(response.getStatusCode().value()).isEqualTo(201);
        assertThat(response.getBody()).isEqualTo(new Ack("status-201"));
    }

    @Test
    void withNoProfileActiveLogbookNeverLogsAnything() {
        try (var capture = new LogCapture("org.zalando.logbook.Logbook", Level.TRACE)) {
            restTemplate.postForEntity("/api/logbook/echo", new EchoRequest("should stay unlogged"),
                    EchoResponse.class);
            assertThat(capture.events()).isEmpty();
        }
    }
}
