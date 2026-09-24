package com.gucardev.kafkaoutboxpatternflightdataupdatesexample.outbox.service;

import com.gucardev.kafkaoutboxpatternflightdataupdatesexample.outbox.config.OutboxProperties;
import com.gucardev.kafkaoutboxpatternflightdataupdatesexample.outbox.repository.OutboxEventRepository;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class OutboxPublisher {

    private final OutboxEventRepository repository;
    private final OutboxKafkaSender sender;
    private final OutboxProperties properties;

    // One event per transaction. A commit failure after Kafka acknowledges can cause redelivery.
    @Transactional
    public boolean publishNext() {
        var claimed = repository.claimNext(Instant.now());
        if (claimed.isEmpty()) {
            return false;
        }
        var event = claimed.get();
        String position;
        try {
            position = sender.send(event);
        } catch (RuntimeException error) {
            var cause = NestedExceptionUtils.getMostSpecificCause(error);
            event.recordFailedAttempt(cause.getClass().getSimpleName() + ": " + cause.getMessage(),
                    properties.retryBackoff(), properties.maxBackoff());
            log.warn("Outbox event {} could not be sent, attempt {}", event.getId(), event.getAttempts(), error);
            return false;
        }
        event.markSent(position);
        return true;
    }
}
