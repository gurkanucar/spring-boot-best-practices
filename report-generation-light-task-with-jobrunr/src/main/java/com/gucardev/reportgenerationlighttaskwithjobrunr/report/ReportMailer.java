package com.gucardev.reportgenerationlighttaskwithjobrunr.report;

import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** Demo adapter. Replace with your mail provider and pass through its idempotency key. */
@Component
@Slf4j
public class ReportMailer {
    public void send(String recipient, UUID reportId, String idempotencyKey) {
        log.info("DEMO: report {} would be emailed to {} (idempotency key {})",
                reportId, recipient, idempotencyKey);
    }
}
