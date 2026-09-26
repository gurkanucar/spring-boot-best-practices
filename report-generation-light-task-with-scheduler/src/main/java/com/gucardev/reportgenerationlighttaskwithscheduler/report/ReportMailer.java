package com.gucardev.reportgenerationlighttaskwithscheduler.report;

import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Demo mail adapter. A real one passes {@code idempotencyKey} to the mail provider: a task can run
 * again after the provider accepted the message, and only the provider can drop the duplicate.
 */
@Component
@Slf4j
public class ReportMailer {

    public void send(String recipient, UUID reportId, String idempotencyKey) {
        log.info("Mail to {} about report {} (idempotency key {})", recipient, reportId, idempotencyKey);
    }
}
