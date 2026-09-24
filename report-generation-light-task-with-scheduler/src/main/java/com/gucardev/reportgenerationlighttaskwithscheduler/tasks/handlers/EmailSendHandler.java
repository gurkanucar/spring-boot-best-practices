package com.gucardev.reportgenerationlighttaskwithscheduler.tasks.handlers;

import com.gucardev.reportgenerationlighttaskwithscheduler.tasks.Payloads;
import com.gucardev.reportgenerationlighttaskwithscheduler.tasks.TaskHandler;
import com.gucardev.reportgenerationlighttaskwithscheduler.tasks.TaskType;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/**
 * Payload: {@code {"reportRequestId": "<uuid>"}}, sends the "your report is ready" email.
 *
 * <p><b>Idempotent:</b> sending an email cannot be rolled back, and the task may run again after
 * the provider accepted the message but before the task was marked SUCCEEDED. Pass a stable
 * idempotency key to the mail provider (e.g. {@code "report-ready:" + reportRequestId}), or record
 * sent emails in a table and check it first.
 */
@Component
@Slf4j
public class EmailSendHandler implements TaskHandler {

    @Override
    public TaskType type() {
        return TaskType.EMAIL_SEND;
    }

    @Override
    public void handle(JsonNode payload) {
        UUID reportRequestId = Payloads.requireUuid(payload, "reportRequestId");
        // TODO: load the recipient and the report link, then call the mail provider with
        //  idempotency key "report-ready:" + reportRequestId.
        log.info("Sending 'report ready' email for report request {}", reportRequestId);
    }
}
