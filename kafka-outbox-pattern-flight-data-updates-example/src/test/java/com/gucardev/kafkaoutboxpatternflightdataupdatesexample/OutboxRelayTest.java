package com.gucardev.kafkaoutboxpatternflightdataupdatesexample;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.Mockito.reset;

import com.gucardev.kafkaoutboxpatternflightdataupdatesexample.outbox.dto.OutboxEventResponse;
import com.gucardev.kafkaoutboxpatternflightdataupdatesexample.outbox.entity.OutboxEvent;
import com.gucardev.kafkaoutboxpatternflightdataupdatesexample.outbox.entity.OutboxStatus;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.KafkaException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class OutboxRelayTest extends ManualRelayTestBase {

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void failedSendKeepsTheFlightsEventsPendingAndInOrderWhileOtherFlightsContinue() {
        String blocked = scheduleFlight();
        String healthy = scheduleFlight();
        commands.delay(blocked, delayByMinutes(20));
        commands.delay(blocked, delayByMinutes(40));

        doThrow(new KafkaException("Broker unavailable", null))
                .when(sender).send(argThat((OutboxEvent e) -> e.getAggregateId().equals(blocked)));

        // The blocked flight's first event is the oldest head: it fails and the poll stops.
        assertThat(publishAvailable()).isZero();
        List<OutboxEventResponse> pending = outboxFor(blocked);
        assertThat(pending).extracting(OutboxEventResponse::status).containsOnly(OutboxStatus.PENDING);
        assertThat(pending).extracting(OutboxEventResponse::attempts).containsExactly(1, 0, 0);
        assertThat(pending.getFirst().lastError()).contains("Broker unavailable");

        // While it backs off, the other flight goes out. The blocked flight's later events wait:
        // publishing them now would put version 2 on Kafka before version 1.
        assertThat(publishAvailable()).isEqualTo(1);
        assertThat(outboxFor(healthy)).extracting(OutboxEventResponse::status).containsExactly(OutboxStatus.SENT);
        assertThat(outboxFor(blocked)).extracting(OutboxEventResponse::status).containsOnly(OutboxStatus.PENDING);

        // The broker is back and the backoff is over.
        reset(sender);
        jdbc.sql("update outbox_event set next_attempt_at = now() where aggregate_id = :id")
                .param("id", blocked).update();
        assertThat(publishAvailable()).isEqualTo(3);

        assertThat(awaitEvents(blocked, 3)).extracting(e -> e.get("version").asLong()).containsExactly(1L, 2L, 3L);
        assertThat(outboxFor(blocked).getFirst().attempts()).isEqualTo(1);
        assertThat(outboxFor(blocked).getFirst().lastError()).isNull();
    }

    @Test
    void theRelayOnlySeesEventsOfCommittedTransactions() throws Exception {
        String id = scheduleFlight();
        assertThat(publishAvailable()).isEqualTo(1);

        var transaction = new TransactionTemplate(transactionManager);
        try (ExecutorService otherInstance = Executors.newSingleThreadExecutor()) {
            transaction.executeWithoutResult(status -> {
                commands.delay(id, delayByMinutes(30));
                try {
                    // The outbox row exists but is not committed: nothing to publish yet.
                    assertThat(otherInstance.submit(this::publishAvailable).get()).isZero();
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            });
        }
        assertThat(publishAvailable()).isEqualTo(1);
        assertThat(awaitEvents(id, 2)).extracting(e -> e.get("eventType").asString())
                .containsExactly("FLIGHT_SCHEDULED", "FLIGHT_DELAYED");
    }

    @Test
    void aLaterRollbackDoesNotReplayPreviouslyCommittedEvents() {
        String id = scheduleFlight();
        commands.delay(id, delayByMinutes(20));
        assertThat(publisher.publishNext()).isTrue();

        // Simulate a DB rollback after Kafka acknowledged the second event.
        new TransactionTemplate(transactionManager).executeWithoutResult(transaction -> {
            assertThat(publisher.publishNext()).isTrue();
            transaction.setRollbackOnly();
        });
        assertThat(outboxFor(id)).extracting(OutboxEventResponse::status)
                .containsExactly(OutboxStatus.SENT, OutboxStatus.PENDING);

        assertThat(publisher.publishNext()).isTrue();
        var received = awaitEvents(id, 3);
        assertThat(received).extracting(e -> e.get("version").asLong()).containsExactly(1L, 2L, 2L);
        assertThat(received.get(1).get("eventId").asString())
                .isEqualTo(received.get(2).get("eventId").asString());
    }

    @Test
    void anotherRelayCanPublishADifferentFlightButCannotOvertakeALockedEvent() throws Exception {
        String blocked = scheduleFlight();
        commands.delay(blocked, delayByMinutes(20));
        String healthy = scheduleFlight();
        var sending = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        doAnswer(invocation -> {
            sending.countDown();
            if (!release.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting for test release");
            }
            return invocation.callRealMethod();
        }).when(sender).send(argThat((OutboxEvent event) -> event.getAggregateId().equals(blocked)));

        try (ExecutorService worker = Executors.newSingleThreadExecutor()) {
            var first = worker.submit(publisher::publishNext);
            try {
                assertThat(sending.await(10, TimeUnit.SECONDS)).isTrue();
                assertThat(publisher.publishNext()).isTrue(); // the healthy flight
                assertThat(outboxFor(healthy).getFirst().status()).isEqualTo(OutboxStatus.SENT);
                assertThat(publisher.publishNext()).isFalse(); // the blocked flight's version 2 must wait
                assertThat(outboxFor(blocked)).extracting(OutboxEventResponse::status)
                        .containsOnly(OutboxStatus.PENDING);
            } finally {
                release.countDown();
            }
            assertThat(first.get(10, TimeUnit.SECONDS)).isTrue();
        }
        assertThat(publisher.publishNext()).isTrue();
        assertThat(awaitEvents(blocked, 2)).extracting(e -> e.get("version").asLong()).containsExactly(1L, 2L);
    }

    @Test
    void outboxInspectionCombinesFlightAndStatusFilters() throws Exception {
        String id = scheduleFlight();
        assertThat(publisher.publishNext()).isTrue();
        commands.delay(id, delayByMinutes(20));

        mockMvc.perform(get("/api/outbox").param("aggregateId", id).param("status", "PENDING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].eventType").value("FLIGHT_DELAYED"))
                .andExpect(jsonPath("$[0].status").value("PENDING"));
        mockMvc.perform(get("/api/outbox").param("aggregateId", id).param("status", "SENT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].eventType").value("FLIGHT_SCHEDULED"))
                .andExpect(jsonPath("$[0].status").value("SENT"));
    }
}
