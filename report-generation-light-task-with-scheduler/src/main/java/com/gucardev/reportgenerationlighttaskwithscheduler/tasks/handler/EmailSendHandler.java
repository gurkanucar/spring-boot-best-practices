package com.gucardev.reportgenerationlighttaskwithscheduler.tasks.handler;

import com.gucardev.reportgenerationlighttaskwithscheduler.tasks.entity.TaskType;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Sends the "your report is ready" email. Payload: the report id.
 *
 * <p><b>Idempotent:</b> sending an email cannot be rolled back, and the task may run again after
 * the provider accepted the message but before the task was marked SUCCEEDED. Pass a stable
 * idempotency key to the mail provider (e.g. {@code "report-ready:" + reportId}). A local sent-email
 * flag alone cannot close the crash window between the external send and the database commit.
 */
@Component
@Slf4j
public class EmailSendHandler implements TaskHandler<UUID> {

    @Override
    public TaskType type() {
        return TaskType.EMAIL_SEND;
    }

    @Override
    public void handle(UUID reportId) {
        // TODO: load the recipient and the report link, then call the mail provider with
        //  idempotency key "report-ready:" + reportId.
        log.info("Sending 'report ready' email for report {}", reportId);
    }
}
