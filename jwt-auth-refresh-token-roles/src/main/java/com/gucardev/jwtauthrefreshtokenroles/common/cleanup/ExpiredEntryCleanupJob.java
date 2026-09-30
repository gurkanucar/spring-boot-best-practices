package com.gucardev.jwtauthrefreshtokenroles.common.cleanup;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Deletes expired rows from every ExpiredEntryCleaner. Expired tokens are already rejected when they
 * are used; this only keeps the tables small. The deletes are idempotent, so running it on several
 * instances at the same time is harmless and no ShedLock is needed.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExpiredEntryCleanupJob {

    private final List<ExpiredEntryCleaner> cleaners;
    private final Clock clock;

    @Scheduled(cron = "${security.token-cleanup-cron}")
    public void cleanUp() {
        Instant now = clock.instant();
        for (ExpiredEntryCleaner cleaner : cleaners) {
            int deleted = cleaner.deleteExpired(now);
            log.info("Deleted {} expired {}", deleted, cleaner.name());
        }
    }
}
