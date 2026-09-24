package com.gucardev.kafkainboxpatternairportsdatafillingexample.inbox.service;

import com.gucardev.kafkainboxpatternairportsdatafillingexample.inbox.entity.InboxStatus;
import com.gucardev.kafkainboxpatternairportsdatafillingexample.inbox.repository.InboxEventRepository;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class InboxProcessor {

    private final InboxEventRepository repository;
    private final InboxEventHandler handler;

    @Scheduled(fixedDelayString = "${app.inbox.poll-interval}", initialDelayString = "${app.inbox.poll-interval}")
    public void poll() {
        // Bound each poll so a busy inbox cannot monopolize the scheduler.
        var candidates = repository.findByStatusAndNextAttemptAtLessThanEqualOrderById(
                InboxStatus.PENDING, Instant.now(), PageRequest.of(0, 100));
        for (var candidate : candidates) {
            try {
                handler.process(candidate.getId(), candidate.getAttempts());
            } catch (RuntimeException error) {
                // process() has rolled back before this separate transaction starts.
                handler.recordFailure(candidate.getId(), candidate.getAttempts(), error);
            }
        }
    }
}
