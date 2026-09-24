package com.gucardev.kafkaoutboxpatternflightdataupdatesexample.outbox.service;

import com.gucardev.kafkaoutboxpatternflightdataupdatesexample.outbox.config.OutboxProperties;
import com.gucardev.kafkaoutboxpatternflightdataupdatesexample.outbox.repository.OutboxEventRepository;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.core.LockAssert;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxCleanupJob {

    private final OutboxEventRepository repository;
    private final OutboxProperties properties;

    @Scheduled(cron = "${app.outbox.cleanup-cron}", zone = "UTC")
    @SchedulerLock(name = "flight-outbox-cleanup", lockAtLeastFor = "PT1M")
    public void cleanup() {
        LockAssert.assertLocked();
        int deleted = repository.deleteSentBefore(Instant.now().minus(properties.retention()));
        log.info("Outbox cleanup removed {} sent events", deleted);
    }
}
