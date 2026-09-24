package com.gucardev.kafkainboxpatternairportsdatafillingexample.inbox.service;

import com.gucardev.kafkainboxpatternairportsdatafillingexample.inbox.config.InboxProperties;
import com.gucardev.kafkainboxpatternairportsdatafillingexample.inbox.entity.InboxStatus;
import com.gucardev.kafkainboxpatternairportsdatafillingexample.inbox.repository.InboxEventRepository;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * The inbox grows with every event. Finished rows are only needed for a while (duplicate
 * detection of late redeliveries, debugging), so old PROCESSED and SKIPPED rows are deleted.
 * FAILED rows are kept until someone has looked at them.
 */
@Component
public class InboxCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(InboxCleanupJob.class);

    private final InboxEventRepository repository;
    private final InboxProperties properties;

    public InboxCleanupJob(InboxEventRepository repository, InboxProperties properties) {
        this.repository = repository;
        this.properties = properties;
    }

    @Scheduled(cron = "${app.inbox.cleanup-cron}")
    public void scheduledCleanup() {
        deleteFinishedBefore(Instant.now().minus(properties.retention()));
    }

    public int deleteFinishedBefore(Instant cutoff) {
        int deleted = repository.deleteFinishedBefore(List.of(InboxStatus.PROCESSED, InboxStatus.SKIPPED), cutoff);
        log.info("Inbox cleanup: deleted {} finished events older than {}", deleted, cutoff);
        return deleted;
    }
}
