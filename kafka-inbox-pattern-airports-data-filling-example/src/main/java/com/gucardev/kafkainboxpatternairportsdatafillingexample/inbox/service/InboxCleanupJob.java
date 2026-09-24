package com.gucardev.kafkainboxpatternairportsdatafillingexample.inbox.service;

import com.gucardev.kafkainboxpatternairportsdatafillingexample.inbox.config.InboxProperties;
import com.gucardev.kafkainboxpatternairportsdatafillingexample.inbox.repository.InboxEventRepository;
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
public class InboxCleanupJob {

    private final InboxEventRepository repository;
    private final InboxProperties properties;

    @Scheduled(cron = "${app.inbox.cleanup-cron}", zone = "UTC")
    @SchedulerLock(name = "airport-inbox-cleanup", lockAtLeastFor = "PT1M")
    public void cleanup() {
        LockAssert.assertLocked();
        int deleted = repository.deleteFinishedBefore(Instant.now().minus(properties.retention()));
        log.info("Inbox cleanup removed {} finished events", deleted);
    }
}
