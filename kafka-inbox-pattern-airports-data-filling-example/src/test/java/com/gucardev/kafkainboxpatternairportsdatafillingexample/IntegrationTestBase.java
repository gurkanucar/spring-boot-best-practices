package com.gucardev.kafkainboxpatternairportsdatafillingexample;

import com.gucardev.kafkainboxpatternairportsdatafillingexample.airport.entity.RunwaySurface;
import com.gucardev.kafkainboxpatternairportsdatafillingexample.event.dto.AirportEvent;
import com.gucardev.kafkainboxpatternairportsdatafillingexample.event.dto.AirportPayload;
import com.gucardev.kafkainboxpatternairportsdatafillingexample.event.dto.RunwayPayload;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

/** One application context (and one pair of containers) shared by all integration tests. */
@SpringBootTest(properties = {
        "app.inbox.poll-interval=100ms",
        "app.inbox.retry-backoff=100ms",
        "app.inbox.max-attempts=3",
        "app.inbox.cleanup-cron=-"
})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
public abstract class IntegrationTestBase {

    protected static final String TOPIC = "airport-events";
    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    @Autowired
    protected MockMvc mockMvc;
    @Autowired
    protected KafkaTemplate<String, String> kafkaTemplate;
    @Autowired
    protected JsonMapper jsonMapper;

    /** The database is shared by all tests, so every test works with its own airport codes. */
    protected static String newAirportCode() {
        int n = SEQUENCE.getAndIncrement();
        return "" + (char) ('A' + n / 676 % 26) + (char) ('A' + n / 26 % 26) + (char) ('A' + n % 26);
    }

    protected static String icaoFor(String code) {
        return "Q" + code;
    }

    protected static AirportEvent event(String code, long version, String name, List<RunwayPayload> runways) {
        return new AirportEvent(UUID.randomUUID().toString(), code, version, Instant.now(),
                new AirportPayload(icaoFor(code), name, "Istanbul", "TR", "Europe/Istanbul", runways));
    }

    protected static AirportEvent event(String code, long version, String name) {
        return event(code, version, name,
                List.of(new RunwayPayload("16L/34R", 3750, RunwaySurface.ASPHALT)));
    }

    protected void publish(String key, String json) throws Exception {
        kafkaTemplate.send(TOPIC, key, json).get();
    }

    protected void publish(AirportEvent event) throws Exception {
        publish(event.airportCode(), jsonMapper.writeValueAsString(event));
    }
}
