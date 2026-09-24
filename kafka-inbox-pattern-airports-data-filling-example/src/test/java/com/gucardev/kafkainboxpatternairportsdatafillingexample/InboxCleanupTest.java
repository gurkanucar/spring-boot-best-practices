package com.gucardev.kafkainboxpatternairportsdatafillingexample;

import static org.assertj.core.api.Assertions.assertThat;

import com.gucardev.kafkainboxpatternairportsdatafillingexample.airport.service.AirportQueryService;
import com.gucardev.kafkainboxpatternairportsdatafillingexample.inbox.entity.InboxSource;
import com.gucardev.kafkainboxpatternairportsdatafillingexample.inbox.entity.InboxStatus;
import com.gucardev.kafkainboxpatternairportsdatafillingexample.inbox.repository.InboxEventRepository;
import com.gucardev.kafkainboxpatternairportsdatafillingexample.inbox.service.InboxCleanupJob;
import com.gucardev.kafkainboxpatternairportsdatafillingexample.inbox.service.InboxEventHandler;
import com.gucardev.kafkainboxpatternairportsdatafillingexample.inbox.service.InboxQueryService;
import com.gucardev.kafkainboxpatternairportsdatafillingexample.inbox.service.InboxWriter;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import javax.sql.DataSource;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;

@SpringBootTest(properties = "app.inbox.poll-interval=1h")
class InboxCleanupTest extends IntegrationTestBase {

    @Autowired
    private InboxWriter writer;
    @Autowired
    private InboxEventHandler handler;
    @Autowired
    private InboxCleanupJob cleanupJob;
    @Autowired
    private InboxEventRepository repository;
    @Autowired
    private InboxQueryService inbox;
    @Autowired
    private AirportQueryService airports;
    @Autowired
    private JdbcClient jdbc;
    @Autowired
    private DataSource dataSource;

    @Test
    void cleanupHonorsTheSharedLockAndOnlyRemovesExpiredFinishedEvents() {
        String code = newAirportCode();
        var original = event(code, 1, "Original");
        long processed = writer.store(original, InboxSource.REST, null).inboxEventId();
        handler.process(processed, 0);
        long skipped = writer.store(event(code, 1, "Stale"), InboxSource.REST, null).inboxEventId();
        handler.process(skipped, 0);
        long recent = writer.store(event(code, 2, "Current"), InboxSource.REST, null).inboxEventId();
        handler.process(recent, 0);
        long pending = writer.store(event(newAirportCode(), 1, "Pending"), InboxSource.REST, null).inboxEventId();
        long failed = writer.store(event(newAirportCode(), 1, "Broken"), InboxSource.REST, null).inboxEventId();
        jdbc.sql("update inbox_event set payload = '{}'::jsonb where id = :id").param("id", failed).update();
        handler.process(failed, 0);

        jdbc.sql("update inbox_event set processed_at = now() - interval '8 days' where id in (:ids)")
                .param("ids", List.of(processed, skipped, failed)).update();
        jdbc.sql("update inbox_event set received_at = now() - interval '8 days' where id = :id")
                .param("id", pending).update();

        // A separate provider represents another application instance using the same database.
        var otherInstance = new JdbcTemplateLockProvider(JdbcTemplateLockProvider.Configuration.builder()
                .withJdbcTemplate(new JdbcTemplate(dataSource)).usingDbTime().build());
        var lock = otherInstance.lock(new LockConfiguration(Instant.now(), "airport-inbox-cleanup",
                Duration.ofMinutes(10), Duration.ZERO)).orElseThrow();
        try {
            cleanupJob.cleanup();
            assertThat(repository.existsById(processed)).isTrue();
            assertThat(repository.existsById(skipped)).isTrue();
        } finally {
            lock.unlock();
        }

        cleanupJob.cleanup();
        assertThat(repository.existsById(processed)).isFalse();
        assertThat(repository.existsById(skipped)).isFalse();
        assertThat(inbox.get(recent).status()).isEqualTo(InboxStatus.PROCESSED);
        assertThat(inbox.get(pending).status()).isEqualTo(InboxStatus.PENDING);
        assertThat(inbox.get(failed).status()).isEqualTo(InboxStatus.FAILED);
        assertThat(airports.get(code).version()).isEqualTo(2);
        assertThat(jdbc.sql("select lock_until > timezone('UTC', now()) from shedlock where name = 'airport-inbox-cleanup'")
                .query(Boolean.class).single()).isTrue();

        // Cleanup ends ID-based deduplication, but an old full snapshot cannot overwrite newer state.
        var redelivery = writer.store(original, InboxSource.KAFKA, null);
        assertThat(redelivery.duplicate()).isFalse();
        handler.process(redelivery.inboxEventId(), 0);
        assertThat(inbox.get(redelivery.inboxEventId()).status()).isEqualTo(InboxStatus.SKIPPED);
        assertThat(airports.get(code).name()).isEqualTo("Current");
    }
}
