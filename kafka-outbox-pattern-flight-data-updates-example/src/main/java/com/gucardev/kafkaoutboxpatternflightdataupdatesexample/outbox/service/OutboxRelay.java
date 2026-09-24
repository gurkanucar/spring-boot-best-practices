package com.gucardev.kafkaoutboxpatternflightdataupdatesexample.outbox.service;

import com.gucardev.kafkaoutboxpatternflightdataupdatesexample.outbox.config.OutboxProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OutboxRelay {

    private final OutboxPublisher publisher;
    private final OutboxProperties properties;

    @Scheduled(fixedDelayString = "${app.outbox.poll-interval}", initialDelayString = "${app.outbox.poll-interval}")
    public void poll() {
        // Each call crosses the transaction proxy; no transaction spans the whole poll.
        for (int i = 0; i < properties.batchSize(); i++) {
            if (Thread.currentThread().isInterrupted() || !publisher.publishNext()) {
                break;
            }
        }
    }
}
