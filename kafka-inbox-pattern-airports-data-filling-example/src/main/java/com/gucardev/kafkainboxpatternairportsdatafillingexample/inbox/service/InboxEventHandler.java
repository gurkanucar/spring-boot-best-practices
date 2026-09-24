package com.gucardev.kafkainboxpatternairportsdatafillingexample.inbox.service;

import com.gucardev.kafkainboxpatternairportsdatafillingexample.airport.service.AirportSnapshotApplier;
import com.gucardev.kafkainboxpatternairportsdatafillingexample.event.dto.AirportEvent;
import com.gucardev.kafkainboxpatternairportsdatafillingexample.inbox.config.InboxProperties;
import com.gucardev.kafkainboxpatternairportsdatafillingexample.inbox.repository.InboxEventRepository;
import jakarta.validation.Validator;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@Service
@RequiredArgsConstructor
@Slf4j
public class InboxEventHandler {

    private final InboxEventRepository repository;
    private final JsonMapper jsonMapper;
    private final Validator validator;
    private final AirportSnapshotApplier applier;
    private final InboxProperties properties;

    @Transactional
    public void process(long id, int attempts) {
        var claimed = repository.lockPending(id, attempts, Instant.now());
        if (claimed.isEmpty()) {
            return;
        }
        var inboxEvent = claimed.get();
        AirportEvent event;
        try {
            event = jsonMapper.readValue(inboxEvent.getPayload(), AirportEvent.class);
        } catch (JacksonException error) {
            inboxEvent.markFailed("Unreadable payload: " + error.getOriginalMessage());
            return;
        }
        if (event == null || !validator.validate(event).isEmpty()) {
            inboxEvent.markFailed("Invalid payload");
            return;
        }
        if (applier.apply(event)) {
            inboxEvent.markProcessed();
        } else {
            inboxEvent.markSkipped();
        }
    }

    @Transactional
    public void recordFailure(long id, int attempts, RuntimeException error) {
        // Another worker may have completed or retried this event after our rollback.
        repository.lockPending(id, attempts, Instant.now()).ifPresent(event -> {
            var cause = NestedExceptionUtils.getMostSpecificCause(error);
            event.recordFailedAttempt(cause.getClass().getSimpleName() + ": " + cause.getMessage(),
                    properties.maxAttempts(), properties.retryBackoff());
            log.warn("Inbox event {} failed (attempt {})", id, event.getAttempts(), error);
        });
    }
}
