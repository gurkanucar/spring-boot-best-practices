package com.gucardev.kafkaoutboxpatternflightdataupdatesexample;

import static org.assertj.core.api.Assertions.assertThat;

import com.gucardev.kafkaoutboxpatternflightdataupdatesexample.flight.service.FlightQueryService;
import com.gucardev.kafkaoutboxpatternflightdataupdatesexample.outbox.dto.OutboxEventResponse;
import com.gucardev.kafkaoutboxpatternflightdataupdatesexample.outbox.entity.OutboxStatus;
import com.gucardev.kafkaoutboxpatternflightdataupdatesexample.outbox.service.OutboxCleanupJob;
import java.time.Duration;
import java.time.Instant;
import javax.sql.DataSource;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class OutboxCleanupTest extends ManualRelayTestBase {

    @Autowired
    private OutboxCleanupJob cleanupJob;
    @Autowired
    private FlightQueryService flights;
    @Autowired
    private DataSource dataSource;

    @Test
    void cleanupHonorsTheSharedLockAndOnlyRemovesOldSentEvents() {
        String id = scheduleFlight();
        commands.delay(id, delayByMinutes(15));
        assertThat(publishAvailable()).isEqualTo(2);
        jdbc.sql("update outbox_event set sent_at = now() - interval '8 days' where aggregate_id = :id")
                .param("id", id).update();

        commands.delay(id, delayByMinutes(25));
        assertThat(publishAvailable()).isEqualTo(1); // recently sent: kept
        String pending = scheduleFlight();
        jdbc.sql("update outbox_event set created_at = now() - interval '8 days' where aggregate_id = :id")
                .param("id", pending).update();

        // A separate provider represents another application instance using the same database.
        var otherInstance = new JdbcTemplateLockProvider(JdbcTemplateLockProvider.Configuration.builder()
                .withJdbcTemplate(new JdbcTemplate(dataSource)).usingDbTime().build());
        var lock = otherInstance.lock(new LockConfiguration(Instant.now(), "flight-outbox-cleanup",
                Duration.ofMinutes(10), Duration.ZERO)).orElseThrow();
        try {
            cleanupJob.cleanup();
            assertThat(outboxFor(id)).hasSize(3);
        } finally {
            lock.unlock();
        }

        cleanupJob.cleanup();
        assertThat(outboxFor(id)).singleElement().satisfies(e -> {
            assertThat(e.eventType()).isEqualTo("FLIGHT_DELAYED");
            assertThat(e.status()).isEqualTo(OutboxStatus.SENT);
        });
        // Old but not yet published: an unsent event is never deleted.
        assertThat(outboxFor(pending)).extracting(OutboxEventResponse::status).containsExactly(OutboxStatus.PENDING);
        assertThat(flights.get(id).version()).isEqualTo(3);
    }
}
