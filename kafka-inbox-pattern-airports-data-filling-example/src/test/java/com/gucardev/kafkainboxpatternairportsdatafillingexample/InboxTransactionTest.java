package com.gucardev.kafkainboxpatternairportsdatafillingexample;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gucardev.kafkainboxpatternairportsdatafillingexample.airport.service.AirportQueryService;
import com.gucardev.kafkainboxpatternairportsdatafillingexample.event.dto.AirportEvent;
import com.gucardev.kafkainboxpatternairportsdatafillingexample.event.dto.AirportPayload;
import com.gucardev.kafkainboxpatternairportsdatafillingexample.inbox.entity.InboxSource;
import com.gucardev.kafkainboxpatternairportsdatafillingexample.inbox.entity.InboxStatus;
import com.gucardev.kafkainboxpatternairportsdatafillingexample.inbox.service.InboxEventHandler;
import com.gucardev.kafkainboxpatternairportsdatafillingexample.inbox.service.InboxQueryService;
import com.gucardev.kafkainboxpatternairportsdatafillingexample.inbox.service.InboxWriter;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;

// Drive transaction boundaries explicitly; the scheduler must not consume the test's events.
@SpringBootTest(properties = "app.inbox.poll-interval=1h")
class InboxTransactionTest extends IntegrationTestBase {

    @Autowired
    private InboxWriter writer;
    @Autowired
    private InboxEventHandler handler;
    @Autowired
    private InboxQueryService inbox;
    @Autowired
    private AirportQueryService airports;
    @Autowired
    private JdbcClient jdbc;

    @Test
    void delayedFailureCannotOverwriteACompletedEvent() {
        String code = newAirportCode();
        long id = writer.store(event(code, 1, "Airport"), InboxSource.REST, null).inboxEventId();

        // A worker succeeds in the gap between another worker's rollback and failure recording.
        handler.process(id, 0);
        handler.recordFailure(id, 0, new IllegalStateException("Late failure"));

        var result = inbox.get(id);
        assertThat(result.status()).isEqualTo(InboxStatus.PROCESSED);
        assertThat(result.attempts()).isZero();
        assertThat(result.lastError()).isNull();
        assertThat(airports.get(code).version()).isEqualTo(1);
    }

    @Test
    void delayedFailureCannotOverwriteANewerAttempt() {
        long id = writer.store(event(newAirportCode(), 1, "Airport"), InboxSource.REST, null).inboxEventId();
        handler.recordFailure(id, 0, new IllegalStateException("First failure"));
        jdbc.sql("update inbox_event set next_attempt_at = now() - interval '1 second' where id = :id")
                .param("id", id).update();

        // Even when it is due again, a stale worker cannot consume the new attempt's retry budget.
        handler.recordFailure(id, 0, new IllegalStateException("Late failure"));
        assertThat(inbox.get(id).attempts()).isEqualTo(1);
        assertThat(inbox.get(id).lastError()).contains("First failure");

        handler.process(id, 0);
        assertThat(inbox.get(id).status()).isEqualTo(InboxStatus.PENDING);
        handler.process(id, 1);
        assertThat(inbox.get(id).status()).isEqualTo(InboxStatus.PROCESSED);
    }

    @Test
    void failedApplyRollsBackAirportAndInboxTogether() {
        String code = newAirportCode();
        String holder = newAirportCode();
        handler.process(writer.store(event(code, 1, "Original"), InboxSource.REST, null).inboxEventId(), 0);
        handler.process(writer.store(event(holder, 1, "Holder"), InboxSource.REST, null).inboxEventId(), 0);

        AirportEvent conflicting = new AirportEvent("rollback-" + code, code, 2L, Instant.now(),
                new AirportPayload(icaoFor(holder), "Should roll back", "Izmir", "TR",
                        "Europe/Istanbul", List.of()));
        long id = writer.store(conflicting, InboxSource.REST, null).inboxEventId();

        assertThatThrownBy(() -> handler.process(id, 0)).isInstanceOf(RuntimeException.class);
        assertThat(inbox.get(id).status()).isEqualTo(InboxStatus.PENDING);
        assertThat(inbox.get(id).attempts()).isZero();
        var airport = airports.get(code);
        assertThat(airport.name()).isEqualTo("Original");
        assertThat(airport.version()).isEqualTo(1);
        assertThat(airport.runways()).hasSize(1);

        handler.recordFailure(id, 0, new IllegalStateException("Constraint failure"));
        assertThat(inbox.get(id).attempts()).isEqualTo(1);
    }
}
