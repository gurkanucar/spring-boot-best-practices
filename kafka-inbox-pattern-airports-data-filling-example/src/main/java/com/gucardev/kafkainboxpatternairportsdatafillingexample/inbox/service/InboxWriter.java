package com.gucardev.kafkainboxpatternairportsdatafillingexample.inbox.service;

import com.gucardev.kafkainboxpatternairportsdatafillingexample.event.dto.AirportEvent;
import com.gucardev.kafkainboxpatternairportsdatafillingexample.inbox.entity.InboxEvent;
import com.gucardev.kafkainboxpatternairportsdatafillingexample.inbox.entity.InboxSource;
import com.gucardev.kafkainboxpatternairportsdatafillingexample.inbox.entity.InboxStatus;
import com.gucardev.kafkainboxpatternairportsdatafillingexample.inbox.repository.InboxEventRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@Component
@RequiredArgsConstructor
public class InboxWriter {

    public record Received(long inboxEventId, InboxStatus status, boolean duplicate) {
    }

    private final JdbcClient jdbc;
    private final JsonMapper jsonMapper;
    private final InboxEventRepository repository;

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
