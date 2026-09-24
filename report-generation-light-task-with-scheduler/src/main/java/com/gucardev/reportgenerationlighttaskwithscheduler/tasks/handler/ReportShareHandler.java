package com.gucardev.reportgenerationlighttaskwithscheduler.tasks.handler;

import com.gucardev.reportgenerationlighttaskwithscheduler.tasks.entity.TaskType;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Sends a finished report to a colleague. The payload has two values, so it is a record; it is
 * stored as {@code {"reportId": "...", "recipientEmail": "..."}}. Both fields are required.
 *
 * <p><b>Idempotent:</b> enqueued with the key {@code "report-share:<reportId>:<recipient>"}, so
 * sharing the same report with the same person twice creates one task. The mail provider call
 * should use the same key, for the case where this task itself runs twice.
 */
@Component
@Slf4j
public class ReportShareHandler implements TaskHandler<ReportShareHandler.Payload> {

    public record Payload(UUID reportId, String recipientEmail) {
    }

    @Override
    public TaskType type() {
        return TaskType.REPORT_SHARE;
    }

    @Override
    public void handle(Payload payload) {
        // TODO: load the report link, then send it to payload.recipientEmail() through the mail
        //  provider with idempotency key "report-share:" + reportId + ":" + recipientEmail.
        log.info("Sharing report {} with {}", payload.reportId(), payload.recipientEmail());
    }
}
