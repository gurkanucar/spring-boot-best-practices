package com.gucardev.kafkainboxpatternairportsdatafillingexample;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gucardev.kafkainboxpatternairportsdatafillingexample.TestcontainersConfiguration.DeadLetterCollector;
import com.gucardev.kafkainboxpatternairportsdatafillingexample.airport.dto.AirportResponse;
import com.gucardev.kafkainboxpatternairportsdatafillingexample.airport.entity.RunwaySurface;
import com.gucardev.kafkainboxpatternairportsdatafillingexample.airport.service.AirportQueryService;
import com.gucardev.kafkainboxpatternairportsdatafillingexample.event.dto.AirportEvent;
import com.gucardev.kafkainboxpatternairportsdatafillingexample.event.dto.AirportPayload;
import com.gucardev.kafkainboxpatternairportsdatafillingexample.event.dto.AirportPayload;
import com.gucardev.kafkainboxpatternairportsdatafillingexample.event.dto.RunwayPayload;
import com.gucardev.kafkainboxpatternairportsdatafillingexample.inbox.dto.InboxEventResponse;
import com.gucardev.kafkainboxpatternairportsdatafillingexample.inbox.entity.InboxSource;
import com.gucardev.kafkainboxpatternairportsdatafillingexample.inbox.entity.InboxStatus;
import com.gucardev.kafkainboxpatternairportsdatafillingexample.inbox.service.InboxAdminService;
import com.gucardev.kafkainboxpatternairportsdatafillingexample.inbox.service.InboxCleanupJob;
import com.gucardev.kafkainboxpatternairportsdatafillingexample.inbox.service.InboxProcessor;
import com.gucardev.kafkainboxpatternairportsdatafillingexample.inbox.service.InboxWriter;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.LongStream;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.support.KafkaHeaders;

class AirportInboxFlowTest extends IntegrationTestBase {

    private static final Duration TIMEOUT = Duration.ofSeconds(20);

    @Autowired
    private AirportQueryService airports;
    @Autowired
    private InboxAdminService inbox;
    @Autowired
    private InboxWriter inboxWriter;
    @Autowired
    private InboxProcessor processor;
    @Autowired
    private InboxCleanupJob cleanupJob;
    @Autowired
    private DeadLetterCollector deadLetters;
    @Autowired
    private JdbcClient jdbc;

    private AirportResponse awaitVersion(String code, long version) {
        await().atMost(TIMEOUT).untilAsserted(() -> assertThat(airportVersion(code)).isEqualTo(version));
        return airports.get(code);
    }

    private long airportVersion(String code) {
        return jdbc.sql("select coalesce(max(version), 0) from airport where code = :code").param("code", code)
                .query(Long.class).single();
    }

    private List<InboxEventResponse> awaitChanges(String code, int count) {
        await().atMost(TIMEOUT).untilAsserted(() -> assertThat(inbox.forAirport(code))
                .hasSize(count)
                .allSatisfy(e -> assertThat(e.status()).isNotEqualTo(InboxStatus.PENDING)));
        return inbox.forAirport(code);
    }

    // ---------------------------------------------------------------- Kafka path

    @Test
    void kafkaEventCreatesTheAirportWithItsDetails() throws Exception {
        String code = newAirportCode();
        publish(event(code, 1, "Istanbul Airport",
                List.of(new RunwayPayload("16L/34R", 3750, RunwaySurface.ASPHALT),
                        new RunwayPayload("17L/35R", 4100, RunwaySurface.CONCRETE))));

        AirportResponse airport = awaitVersion(code, 1);
        assertThat(airport.name()).isEqualTo("Istanbul Airport");
        assertThat(airport.runways()).extracting(AirportResponse.RunwayResponse::designator).containsExactly("16L/34R", "17L/35R");

        InboxEventResponse change = awaitChanges(code, 1).getFirst();
        assertThat(change.source()).isEqualTo(InboxSource.KAFKA);
        assertThat(change.status()).isEqualTo(InboxStatus.PROCESSED);
        assertThat(change.kafkaPosition()).startsWith("airport-events-");
    }

    @Test
    void redeliveredMessageIsStoredAndAppliedOnlyOnce() throws Exception {
        String code = newAirportCode();
        String json = jsonMapper.writeValueAsString(event(code, 1, "Once"));

        publish(code, json);
        publish(code, json); // same transactionId: what a redelivery after a crash looks like

        awaitVersion(code, 1);
        Thread.sleep(500); // give the second message time to arrive
        assertThat(inbox.forAirport(code)).hasSize(1);
    }

    @Test
    void newerVersionReplacesTheStateAndSyncsDetailsByNaturalKey() throws Exception {
        String code = newAirportCode();
        publish(event(code, 1, "Old name",
                List.of(new RunwayPayload("09/27", 2000, RunwaySurface.ASPHALT),
                        new RunwayPayload("18/36", 1500, RunwaySurface.GRASS))));
        long runwayId = awaitVersion(code, 1).runways().getFirst().id(); // 09/27

        publish(event(code, 2, "New name",
                List.of(new RunwayPayload("09/27", 2400, RunwaySurface.CONCRETE),   // changed
                        new RunwayPayload("05/23", 3000, RunwaySurface.ASPHALT))));  // new; 18/36 removed
        AirportResponse airport = awaitVersion(code, 2);

        assertThat(airport.name()).isEqualTo("New name");
        assertThat(airport.runways()).extracting(AirportResponse.RunwayResponse::designator).containsExactly("05/23", "09/27");
        AirportResponse.RunwayResponse updated = airport.runways().get(1);
        assertThat(updated.id()).isEqualTo(runwayId); // updated in place, not deleted and re-inserted
        assertThat(updated.lengthMeters()).isEqualTo(2400);
        assertThat(updated.surface()).isEqualTo(RunwaySurface.CONCRETE);
    }

    @Test
    void olderOrEqualVersionIsSkipped() throws Exception {
        String code = newAirportCode();
        publish(event(code, 5, "Version 5"));
        awaitVersion(code, 5);

        publish(event(code, 4, "Version 4 arriving late"));
        publish(event(code, 5, "Another version 5"));

        List<InboxEventResponse> changes = awaitChanges(code, 3);
        assertThat(changes).extracting(InboxEventResponse::status)
                .containsExactly(InboxStatus.PROCESSED, InboxStatus.SKIPPED, InboxStatus.SKIPPED);
        assertThat(changes.get(1).lastError()).isEqualTo("Stale: event version 4, airport already at version 5");
        assertThat(airports.get(code).name()).isEqualTo("Version 5");
    }

    @Test
    void invalidMessagesGoToTheDeadLetterTopicAndNeverReachTheInbox() throws Exception {
        String code = newAirportCode();
        deadLetters.records().clear();

        publish(code, "{ this is not json");
        AirportEvent wrongKey = event(code, 1, "Wrong key");
        publish("ZZZ", jsonMapper.writeValueAsString(wrongKey));
        AirportEvent invalid = event(code, 1, "", List.of()); // blank name
        publish(code, jsonMapper.writeValueAsString(invalid));

        List<ConsumerRecord<String, String>> received = new ArrayList<>();
        await().atMost(TIMEOUT).untilAsserted(() -> {
            deadLetters.records().drainTo(received);
            assertThat(received).extracting(ConsumerRecord::key).contains(code, "ZZZ");
            assertThat(received.stream().filter(r -> r.key().equals(code)).count()).isEqualTo(2);
        });
        // Spring Kafka adds the reason as a header
        ConsumerRecord<String, String> dead = received.stream().filter(r -> r.key().equals("ZZZ")).findFirst().orElseThrow();
        assertThat(new String(dead.headers().lastHeader(KafkaHeaders.DLT_EXCEPTION_MESSAGE).value()))
                .contains("does not match airportCode");
        assertThat(inbox.forAirport(code)).isEmpty();
    }

    // ---------------------------------------------------------------- REST path

    @Test
    void restPutGoesThroughTheInboxAndIsIdempotent() throws Exception {
        String code = newAirportCode();
        String body = """
                {"transactionId": "rest-%s-1", "version": 1,
                 "airport": {"icaoCode": "%s", "name": "Via REST", "city": "Ankara", "countryCode": "TR",
                             "timezone": "Europe/Istanbul",
                             "runways": [{"designator": "03L/21R", "lengthMeters": 3750, "surface": "CONCRETE"}]}}""".formatted(code, icaoFor(code));

        mockMvc.perform(put("/api/airports/" + code).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isAccepted())
                .andExpect(header().string("Location", startsWith("/api/inbox/")))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.duplicate").value(false));

        assertThat(awaitVersion(code, 1).name()).isEqualTo("Via REST");

        // the same request again (client retry): nothing new is stored
        mockMvc.perform(put("/api/airports/" + code).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.duplicate").value(true))
                .andExpect(jsonPath("$.status").value("PROCESSED"));
        mockMvc.perform(get("/api/airports/" + code + "/changes"))
                .andExpect(jsonPath("$[*].source", contains("REST")));
    }

    @Test
    void restAndKafkaFollowTheSameVersionRule() throws Exception {
        String code = newAirportCode();
        mockMvc.perform(put("/api/airports/" + code).contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(event(code, 3, "REST v3")).replace("\"airportCode\":\"" + code + "\",", "")))
                .andExpect(status().isAccepted());
        awaitVersion(code, 3);

        publish(event(code, 2, "Kafka v2"));

        List<InboxEventResponse> changes = awaitChanges(code, 2);
        assertThat(changes).extracting(InboxEventResponse::source).containsExactly(InboxSource.REST, InboxSource.KAFKA);
        assertThat(changes.get(1).status()).isEqualTo(InboxStatus.SKIPPED);
        assertThat(airports.get(code).name()).isEqualTo("REST v3");
    }

    @Test
    void invalidRestRequestIs400WithTheSameRulesAsKafka() throws Exception {
        String code = newAirportCode();
        mockMvc.perform(put("/api/airports/" + code).contentType(MediaType.APPLICATION_JSON).content("""
                        {"version": 0,
                         "airport": {"icaoCode": "12", "name": "X", "city": "Y", "countryCode": "TR",
                                     "timezone": "Mars/Base",
                                     "runways": [{"designator": "09/27", "lengthMeters": 100, "surface": "ASPHALT"},
                                                 {"designator": "09/27", "lengthMeters": 200, "surface": "ASPHALT"}]}}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", contains(
                        "airport.icaoCode: must be a 4-letter ICAO code",
                        "airport.runwayDesignatorsUnique: runway designators must be unique",
                        "airport.timezoneKnown: must be a known time zone, e.g. Europe/Istanbul",
                        "transactionId: must not be blank",
                        "version: must be greater than 0")));

        mockMvc.perform(put("/api/airports/istanbul").contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(event(code, 1, "Lowercase code"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", contains("code: must be a 3-letter IATA code")));

        assertThat(inbox.forAirport(code)).isEmpty();
    }

    // ---------------------------------------------------------------- failures and operations

    @Test
    void failingEventIsRetriedThenFailedAndCanBeRetriedManuallyAfterTheFix() throws Exception {
        String holder = newAirportCode();
        String newcomer = newAirportCode();
        publish(event(holder, 1, "Holds the ICAO code"));
        awaitVersion(holder, 1);

        // newcomer claims the holder's ICAO code: unique violation on every attempt
        AirportEvent conflicting = new AirportEvent("conflict-" + newcomer, newcomer, 1L, Instant.now(),
                new AirportPayload(icaoFor(holder), "Newcomer", "Izmir", "TR", "Europe/Istanbul", List.of()));
        publish(conflicting);

        await().atMost(TIMEOUT).untilAsserted(() -> assertThat(inbox.forAirport(newcomer))
                .singleElement().satisfies(e -> {
                    assertThat(e.status()).isEqualTo(InboxStatus.FAILED);
                    assertThat(e.attempts()).isEqualTo(3);
                    assertThat(e.lastError()).contains("airport_icao_code_key");
                }));
        long failedId = inbox.forAirport(newcomer).getFirst().id();
        mockMvc.perform(get("/api/inbox").param("status", "FAILED"))
                .andExpect(jsonPath("$[*].id", hasItem((int) failedId)));

        // fix the cause: the holder gets a new ICAO code (newer version)
        AirportEvent fix = new AirportEvent("fix-" + holder, holder, 2L, Instant.now(),
                new AirportPayload("Z" + holder, "Holds the ICAO code", "Istanbul", "TR", "Europe/Istanbul", List.of()));
        publish(fix);
        awaitVersion(holder, 2);

        mockMvc.perform(post("/api/inbox/" + failedId + "/retry"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"));
        assertThat(awaitVersion(newcomer, 1).icaoCode()).isEqualTo(icaoFor(holder));
        assertThat(inbox.get(failedId).status()).isEqualTo(InboxStatus.PROCESSED);

        mockMvc.perform(post("/api/inbox/" + failedId + "/retry"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail", containsString("Only FAILED events can be retried")));
    }

    @Test
    void brokenStoredPayloadFailsImmediatelyWithoutRetries() {
        String code = newAirportCode();
        jdbc.sql("""
                        insert into inbox_event (transaction_id, source, airport_code, version, payload, status,
                                                 attempts, received_at, next_attempt_at)
                        values (:tx, 'REST', :code, 1, '{"unexpected": true}'::jsonb, 'PENDING', 0, now(), now())""")
                .param("tx", "broken-" + code).param("code", code).update();

        await().atMost(TIMEOUT).untilAsserted(() -> assertThat(inbox.forAirport(code)).singleElement().satisfies(e -> {
            assertThat(e.status()).isEqualTo(InboxStatus.FAILED);
            assertThat(e.attempts()).isEqualTo(1);
            assertThat(e.lastError()).startsWith("Invalid payload: ");
        }));
    }

    @Test
    void concurrentProcessorsApplyEveryEventExactlyOnceAndTheHighestVersionWins() throws Exception {
        String code = newAirportCode();
        List<Long> versions = new ArrayList<>(LongStream.rangeClosed(1, 40).boxed().toList());
        Collections.shuffle(versions);
        for (long version : versions) {
            inboxWriter.store(event(code, version, "v" + version), InboxSource.KAFKA, null);
        }

        // four extra "instances" competing with the scheduled poller for the same rows
        try (ExecutorService pool = Executors.newFixedThreadPool(4)) {
            List<Future<?>> workers = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                workers.add(pool.submit(processor::poll));
            }
            for (Future<?> worker : workers) {
                worker.get();
            }
        }

        List<InboxEventResponse> changes = awaitChanges(code, 40);
        assertThat(changes).extracting(InboxEventResponse::status)
                .doesNotContain(InboxStatus.FAILED)
                .containsOnly(InboxStatus.PROCESSED, InboxStatus.SKIPPED);
        AirportResponse airport = airports.get(code);
        assertThat(airport.version()).isEqualTo(40);
        assertThat(airport.name()).isEqualTo("v40");
    }

    @Test
    void cleanupDeletesOldFinishedEventsButKeepsFailedOnes() throws Exception {
        String code = newAirportCode();
        publish(event(code, 1, "To be cleaned"));
        awaitChanges(code, 1);
        long failedBefore = inbox.list(InboxStatus.FAILED).size();

        // the scheduled entry point (self-invocation inside the job) must run in a transaction too
        assertThatNoException().isThrownBy(cleanupJob::scheduledCleanup);

        int deleted = cleanupJob.deleteFinishedBefore(Instant.now().plusSeconds(1));

        assertThat(deleted).isPositive();
        assertThat(inbox.forAirport(code)).isEmpty();
        assertThat(airports.get(code).version()).isEqualTo(1); // the airport itself is untouched
        assertThat(inbox.list(InboxStatus.FAILED)).hasSize((int) failedBefore);
    }
}
