package com.gucardev.kafkaoutboxpatternflightdataupdatesexample;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.gucardev.kafkaoutboxpatternflightdataupdatesexample.TestcontainersConfiguration.FlightUpdatesCollector;
import com.gucardev.kafkaoutboxpatternflightdataupdatesexample.flight.dto.DelayRequest;
import com.gucardev.kafkaoutboxpatternflightdataupdatesexample.flight.dto.ScheduleFlightRequest;
import com.gucardev.kafkaoutboxpatternflightdataupdatesexample.flight.service.FlightCommandService;
import com.gucardev.kafkaoutboxpatternflightdataupdatesexample.outbox.dto.OutboxEventResponse;
import com.gucardev.kafkaoutboxpatternflightdataupdatesexample.outbox.service.OutboxQueryService;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** One application context (and one pair of containers) shared by the integration tests. */
@SpringBootTest(properties = {
        "app.outbox.poll-interval=100ms",
        "app.outbox.retry-backoff=100ms",
        "app.outbox.cleanup-cron=-"
})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
public abstract class IntegrationTestBase {

    protected static final Duration TIMEOUT = Duration.ofSeconds(20);
    protected static final LocalDate FLIGHT_DATE = LocalDate.of(2026, 9, 24);
    protected static final Instant STD = Instant.parse("2026-09-24T18:40:00Z");
    protected static final Instant STA = Instant.parse("2026-09-24T22:30:00Z");
    private static final AtomicInteger SEQUENCE = new AtomicInteger(1000);

    @Autowired
    protected MockMvc mockMvc;
    @Autowired
    protected JsonMapper jsonMapper;
    @Autowired
    protected FlightCommandService commands;
    @Autowired
    protected OutboxQueryService outbox;
    @Autowired
    protected FlightUpdatesCollector flightUpdates;
    @Autowired
    protected JdbcClient jdbc;

    /** The database and topic are shared by all tests, so every test works with its own flights. */
    protected static String newFlightNumber() {
        return String.valueOf(SEQUENCE.getAndIncrement());
    }

    protected static String flightId(String flightNumber) {
        return "TK" + flightNumber + "-20260924-IST";
    }

    /** Istanbul to London Heathrow, an A321neo at gate A5 of terminal 1. */
    protected static ScheduleFlightRequest scheduleRequest(String flightNumber) {
        return new ScheduleFlightRequest("TK", flightNumber, FLIGHT_DATE, "IST", "LHR", STD, STA,
                "1", "A5", "TC-LGA", "A21N");
    }

    protected String scheduleFlight() {
        return commands.schedule(scheduleRequest(newFlightNumber())).flightId();
    }

    protected static DelayRequest delayByMinutes(long minutes) {
        return new DelayRequest(STD.plus(Duration.ofMinutes(minutes)), null, "93", "Late inbound aircraft");
    }

    protected List<OutboxEventResponse> outboxFor(String flightId) {
        return outbox.list(null, flightId);
    }

    /** Waits until exactly {@code count} events of the flight arrived and returns them parsed. */
    protected List<JsonNode> awaitEvents(String flightId, int count) {
        await().atMost(TIMEOUT).untilAsserted(() -> assertThat(flightUpdates.recordsFor(flightId)).hasSize(count));
        return flightUpdates.recordsFor(flightId).stream().map(r -> jsonMapper.readTree(r.value())).toList();
    }

    protected static String kafkaHeader(ConsumerRecord<String, String> record, String name) {
        return new String(record.headers().lastHeader(name).value(), StandardCharsets.UTF_8);
    }
}
