package com.gucardev.kafkaoutboxpatternflightdataupdatesexample;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gucardev.kafkaoutboxpatternflightdataupdatesexample.event.dto.FlightSnapshot;
import com.gucardev.kafkaoutboxpatternflightdataupdatesexample.flight.service.FlightQueryService;
import com.gucardev.kafkaoutboxpatternflightdataupdatesexample.outbox.dto.OutboxEventResponse;
import com.gucardev.kafkaoutboxpatternflightdataupdatesexample.outbox.entity.OutboxStatus;
import com.gucardev.kafkaoutboxpatternflightdataupdatesexample.outbox.service.OutboxKafkaSender;
import com.gucardev.kafkaoutboxpatternflightdataupdatesexample.outbox.service.OutboxRelay;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;

class FlightOutboxFlowTest extends IntegrationTestBase {

    @Autowired
    private FlightQueryService flights;
    @Autowired
    private OutboxRelay relay;
    @Autowired
    private PlatformTransactionManager transactionManager;

    private ResultActions send(String method, String url, String json) throws Exception {
        var request = method.equals("PUT") ? put(url) : post(url);
        return mockMvc.perform(request.contentType(MediaType.APPLICATION_JSON).content(json == null ? "" : json));
    }

    // ---------------------------------------------------------------- publishing

    @Test
    void schedulingAFlightPublishesFlightScheduled() throws Exception {
        String number = newFlightNumber();
        String id = flightId(number);

        send("POST", "/api/flights", jsonMapper.writeValueAsString(scheduleRequest(number)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/flights/" + id))
                .andExpect(jsonPath("$.status").value("SCHEDULED"))
                .andExpect(jsonPath("$.version").value(1));

        JsonNode event = awaitEvents(id, 1).getFirst();
        assertThat(event.get("eventType").asString()).isEqualTo("FLIGHT_SCHEDULED");
        assertThat(event.get("flightId").asString()).isEqualTo(id);
        assertThat(event.get("version").asLong()).isEqualTo(1);
        assertThat(event.get("flight").get("gate").asString()).isEqualTo("A5");
        assertThat(event.get("change").get("scheduledDeparture").asString()).isEqualTo("2026-09-24T18:40:00Z");

        ConsumerRecord<String, String> record = flightUpdates.recordsFor(id).getFirst();
        assertThat(kafkaHeader(record, OutboxKafkaSender.EVENT_ID_HEADER)).isEqualTo(event.get("eventId").asString());
        assertThat(kafkaHeader(record, OutboxKafkaSender.EVENT_TYPE_HEADER)).isEqualTo("FLIGHT_SCHEDULED");

        await().atMost(TIMEOUT).untilAsserted(() -> assertThat(outboxFor(id)).singleElement().satisfies(e -> {
            assertThat(e.status()).isEqualTo(OutboxStatus.SENT);
            assertThat(e.kafkaPosition()).isEqualTo("flight-updates-" + record.partition() + "@" + record.offset());
        }));
    }

    @Test
    void aDayOfOperationsIsPublishedInOrderOnOnePartition() throws Exception {
        String id = scheduleFlight();
        String base = "/api/flights/" + id;

        send("POST", base + "/delay", """
                {"estimatedDeparture": "2026-09-24T19:40:00Z", "delayCode": "93", "reason": "Late inbound aircraft"}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.departureDelayMinutes").value(60))
                .andExpect(jsonPath("$.estimatedArrival").value("2026-09-24T23:30:00Z")); // block time kept
        send("PUT", base + "/gate", """
                {"terminal": "1", "gate": "F12"}""").andExpect(status().isOk());
        send("PUT", base + "/aircraft", """
                {"registration": "TC-LSB", "type": "A21N"}""").andExpect(status().isOk());
        send("POST", base + "/boarding", null).andExpect(status().isOk());
        send("POST", base + "/departure", """
                {"at": "2026-09-24T19:52:00Z"}""").andExpect(status().isOk());
        send("POST", base + "/arrival", """
                {"at": "2026-09-24T23:41:00Z"}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ARRIVED"))
                .andExpect(jsonPath("$.version").value(7));

        List<JsonNode> events = awaitEvents(id, 7);
        assertThat(events).extracting(e -> e.get("eventType").asString()).containsExactly(
                "FLIGHT_SCHEDULED", "FLIGHT_DELAYED", "GATE_CHANGED", "AIRCRAFT_CHANGED",
                "BOARDING_STARTED", "FLIGHT_DEPARTED", "FLIGHT_ARRIVED");
        assertThat(events).extracting(e -> e.get("version").asLong()).containsExactly(1L, 2L, 3L, 4L, 5L, 6L, 7L);
        assertThat(flightUpdates.recordsFor(id)).extracting(ConsumerRecord::partition).containsOnly(
                flightUpdates.recordsFor(id).getFirst().partition());

        JsonNode delayed = events.get(1).get("change");
        assertThat(delayed.get("previousEstimatedDeparture").asString()).isEqualTo("2026-09-24T18:40:00Z");
        assertThat(delayed.get("delayMinutes").asLong()).isEqualTo(60);
        assertThat(delayed.get("delayCode").asString()).isEqualTo("93");
        JsonNode gate = events.get(2).get("change");
        assertThat(gate.get("previousGate").asString()).isEqualTo("A5");
        assertThat(gate.get("gate").asString()).isEqualTo("F12");
        assertThat(events.get(4).get("change").get("gate").asString()).isEqualTo("F12");
        assertThat(events.get(5).get("change").get("delayMinutes").asLong()).isEqualTo(72);
        assertThat(events.get(6).get("flight").get("status").asString()).isEqualTo("ARRIVED");
    }

    @Test
    void divertedFlightArrivesAtTheDiversionAirport() throws Exception {
        String id = scheduleFlight();
        String base = "/api/flights/" + id;
        send("POST", base + "/boarding", null).andExpect(status().isOk());
        send("POST", base + "/departure", """
                {"at": "2026-09-24T18:50:00Z"}""").andExpect(status().isOk());
        send("POST", base + "/diversion", """
                {"divertedTo": "STN", "reason": "Weather at destination"}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DIVERTED"));
        send("POST", base + "/arrival", """
                {"at": "2026-09-24T22:45:00Z"}""").andExpect(status().isOk());

        List<JsonNode> events = awaitEvents(id, 5);
        assertThat(events.get(3).get("eventType").asString()).isEqualTo("FLIGHT_DIVERTED");
        assertThat(events.get(4).get("change").get("arrivalAirport").asString()).isEqualTo("STN");
    }

    // ---------------------------------------------------------------- rejected commands publish nothing

    @Test
    void commandsThatDoNotFitTheLifecycleAre409AndPublishNothing() throws Exception {
        String number = newFlightNumber();
        String id = flightId(number);
        String base = "/api/flights/" + id;
        send("POST", "/api/flights", jsonMapper.writeValueAsString(scheduleRequest(number)))
                .andExpect(status().isCreated());

        send("POST", "/api/flights", jsonMapper.writeValueAsString(scheduleRequest(number)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Flight " + id + " is already scheduled"));
        send("POST", base + "/arrival", null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Cannot record the arrival of flight " + id + " in status SCHEDULED"));
        send("POST", base + "/cancellation", """
                {"reason": "Crew shortage"}""").andExpect(status().isOk());
        send("POST", base + "/boarding", null).andExpect(status().isConflict());
        send("POST", "/api/flights/XX1-20260924-IST/boarding", null).andExpect(status().isNotFound());

        assertThat(outboxFor(id)).extracting(OutboxEventResponse::eventType)
                .containsExactly("FLIGHT_SCHEDULED", "FLIGHT_CANCELLED");
        List<JsonNode> events = awaitEvents(id, 2);
        assertThat(events.get(1).get("change").get("reason").asString()).isEqualTo("Crew shortage");
    }

    @Test
    void invalidRequestsAre400Or422AndPublishNothing() throws Exception {
        String number = newFlightNumber();
        var request = scheduleRequest(number);
        String json = jsonMapper.writeValueAsString(request);

        send("POST", "/api/flights", json.replace("\"TK\"", "\"tk\"").replace("\"TC-LGA\"", "\"TC LGA\""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", contains(
                        "aircraftRegistration: must be an aircraft registration, e.g. TC-LGA",
                        "carrierCode: must be a 2-character IATA airline code")));

        send("POST", "/api/flights", json.replace("\"LHR\"", "\"IST\""))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value("destination must differ from origin"));

        send("POST", "/api/flights", json).andExpect(status().isCreated());
        send("POST", "/api/flights/" + flightId(number) + "/delay", """
                {"estimatedDeparture": "2026-09-24T18:00:00Z", "delayCode": "7"}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", contains("delayCode: must be a 2-digit IATA delay code")));
        send("POST", "/api/flights/" + flightId(number) + "/delay", """
                {"estimatedDeparture": "2026-09-24T18:00:00Z", "delayCode": "93"}""")
                .andExpect(status().isUnprocessableContent());

        assertThat(outboxFor(flightId(number))).hasSize(1);
    }

    @Test
    void aCommandThatChangesNothingPublishesNothing() throws Exception {
        String id = scheduleFlight();
        send("PUT", "/api/flights/" + id + "/gate", """
                {"terminal": "1", "gate": "A5"}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1));
        assertThat(outboxFor(id)).hasSize(1);
    }

    // ---------------------------------------------------------------- atomicity

    @Test
    void rolledBackBusinessTransactionAlsoDiscardsItsEvent() {
        String id = scheduleFlight();

        // The command joins a larger transaction that fails afterwards: the flight change and its
        // outbox row are rolled back together. A direct Kafka send here could not be taken back.
        var transaction = new TransactionTemplate(transactionManager);
        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            commands.delay(id, delayByMinutes(45));
            throw new IllegalStateException("Crew planning system unavailable");
        })).isInstanceOf(IllegalStateException.class);

        FlightSnapshot flight = flights.get(id);
        assertThat(flight.version()).isEqualTo(1);
        assertThat(flight.estimatedDeparture()).isEqualTo(STD);
        assertThat(outboxFor(id)).extracting(OutboxEventResponse::eventType).containsExactly("FLIGHT_SCHEDULED");
    }

    // ---------------------------------------------------------------- multiple relays

    @Test
    void concurrentRelaysPublishEveryEventOnceAndInOrderPerFlight() throws Exception {
        List<String> ids = IntStream.range(0, 10).mapToObj(i -> scheduleFlight()).toList();
        for (int minutes = 10; minutes <= 40; minutes += 10) {
            for (String id : ids) {
                commands.delay(id, delayByMinutes(minutes));
            }
        }

        // four extra "instances" competing with the scheduled relay for the same rows
        try (ExecutorService pool = Executors.newFixedThreadPool(4)) {
            List<Future<?>> workers = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                workers.add(pool.submit(() -> IntStream.range(0, 5).forEach(n -> relay.poll())));
            }
            for (Future<?> worker : workers) {
                worker.get();
            }
        }

        for (String id : ids) {
            List<JsonNode> events = awaitEvents(id, 5);
            assertThat(events).extracting(e -> e.get("version").asLong()).containsExactly(1L, 2L, 3L, 4L, 5L);
            assertThat(outboxFor(id)).extracting(OutboxEventResponse::status).containsOnly(OutboxStatus.SENT);
        }
    }
}
