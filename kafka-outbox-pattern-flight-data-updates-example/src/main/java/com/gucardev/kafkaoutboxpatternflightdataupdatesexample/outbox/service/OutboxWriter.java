package com.gucardev.kafkaoutboxpatternflightdataupdatesexample.outbox.service;

import com.gucardev.kafkaoutboxpatternflightdataupdatesexample.event.dto.FlightEvent;
import com.gucardev.kafkaoutboxpatternflightdataupdatesexample.outbox.entity.OutboxEvent;
import com.gucardev.kafkaoutboxpatternflightdataupdatesexample.outbox.repository.OutboxEventRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@Component
public class OutboxWriter {

    private final OutboxEventRepository repository;
    private final JsonMapper jsonMapper;
    private final String topic;

    public OutboxWriter(OutboxEventRepository repository, JsonMapper jsonMapper,
                        @Value("${app.kafka.flight-updates-topic}") String topic) {
        this.repository = repository;
        this.jsonMapper = jsonMapper;
        this.topic = topic;
    }

    // Refuse standalone writes: the event must commit with its flight change.
    @Transactional(propagation = Propagation.MANDATORY)
    public void append(FlightEvent event) {
        repository.save(OutboxEvent.pending(event.eventId(), "Flight", event.flightId(),
                event.eventType().name(), topic, jsonMapper.writeValueAsString(event)));
    }
}
