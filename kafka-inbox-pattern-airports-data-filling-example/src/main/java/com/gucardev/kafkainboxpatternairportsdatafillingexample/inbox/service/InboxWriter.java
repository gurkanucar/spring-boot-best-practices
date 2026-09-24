package com.gucardev.kafkainboxpatternairportsdatafillingexample.inbox.service;

import com.gucardev.kafkainboxpatternairportsdatafillingexample.event.dto.AirportEvent;
import com.gucardev.kafkainboxpatternairportsdatafillingexample.inbox.entity.InboxEvent;
import com.gucardev.kafkainboxpatternairportsdatafillingexample.inbox.entity.InboxSource;
import com.gucardev.kafkainboxpatternairportsdatafillingexample.inbox.entity.InboxStatus;
import com.gucardev.kafkainboxpatternairportsdatafillingexample.inbox.repository.InboxEventRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * The receiving side of the inbox: stores an event and nothing else. No business logic runs
 * here, so receiving is fast and can only fail for infrastructure reasons.
 */
@Component
public class InboxWriter {

    /** The result for the sender: where the event is, and whether it had been received before. */
    public record Received(long inboxEventId, InboxStatus status, boolean duplicate) {
    }

    private final JdbcClient jdbc;
    private final JsonMapper jsonMapper;
    private final InboxEventRepository repository;

    public InboxWriter(JdbcClient jdbc, JsonMapper jsonMapper, InboxEventRepository repository) {
        this.jdbc = jdbc;
        this.jsonMapper = jsonMapper;
        this.repository = repository;
    }

    /**
     * {@code ON CONFLICT (transaction_id) DO NOTHING} makes storing idempotent in a single
     * statement: a redelivered Kafka message or a retried REST call inserts nothing and is
     * reported as a duplicate. No exception, no check-then-insert race between instances.
     *
     * @param kafkaPosition topic-partition@offset for Kafka events, null for REST
     */
    @Transactional
    public Received store(AirportEvent event, InboxSource source, String kafkaPosition) {
        Timestamp now = Timestamp.from(Instant.now());
        Optional<Long> insertedId = jdbc.sql("""
                        insert into inbox_event (transaction_id, source, airport_code, version, payload, status,
                                                 attempts, kafka_position, received_at, next_attempt_at)
                        values (:transactionId, :source, :airportCode, :version, cast(:payload as jsonb), 'PENDING',
                                0, :kafkaPosition, :now, :now)
                        on conflict (transaction_id) do nothing
                        returning id""")
                .param("transactionId", event.transactionId())
                .param("source", source.name())
                .param("airportCode", event.airportCode())
                .param("version", event.version())
                .param("payload", jsonMapper.writeValueAsString(event))
                .param("kafkaPosition", kafkaPosition)
                .param("now", now)
                .query(Long.class)
                .optional();

        if (insertedId.isPresent()) {
            return new Received(insertedId.get(), InboxStatus.PENDING, false);
        }
        InboxEvent existing = repository.findByTransactionId(event.transactionId()).orElseThrow();
        return new Received(existing.getId(), existing.getStatus(), true);
    }
}
