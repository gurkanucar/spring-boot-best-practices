package com.gucardev.reportgenerationlighttaskwithjobrunr.tasks;

import com.gucardev.reportgenerationlighttaskwithjobrunr.tasks.handler.EmailSendHandler;
import com.gucardev.reportgenerationlighttaskwithjobrunr.tasks.handler.ReportGenerationHandler;
import com.gucardev.reportgenerationlighttaskwithjobrunr.tasks.handler.ReportShareHandler;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jobrunr.jobs.annotations.Job;
import org.jobrunr.jobs.annotations.Recurring;
import org.jobrunr.scheduling.JobScheduler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** Bridges committed task submissions to JobRunr OSS. */
@Component
@RequiredArgsConstructor
@Slf4j
public class TaskDispatcher {

    private final TaskSubmissionRepository submissions;
    private final JobScheduler scheduler;
    private final JsonMapper json;

    /** Call after the business service has returned and released its transaction/connection. */
    public void tryDispatch(UUID id) {
        try {
            dispatch(id);
        } catch (Exception e) {
            log.warn("Task {} awaits handoff to JobRunr", id, e);
        }
    }

    @Recurring(id = "dispatch-task-submissions", interval = "${tasks.dispatch-interval:PT30S}")
    @Job(name = "Submit pending tasks")
    public void dispatchPending() {
        for (TaskSubmission submission : submissions.findTop100BySubmittedAtIsNullOrderByCreatedAtAsc()) {
            dispatch(submission.getId());
        }
    }

    public void dispatch(UUID id) {
        var submission = submissions.findById(id).orElseThrow();
        if (submission.getSubmittedAt() != null) {
            return;
        }
        String payload = submission.getPayload();
        switch (submission.getType()) {
            case REPORT_GENERATION -> {
                Long requestId = json.readValue(payload, Long.class);
                scheduler.<ReportGenerationHandler>enqueue(id, handler -> handler.handle(requestId));
            }
            case EMAIL_SEND -> {
                UUID reportId = json.readValue(payload, UUID.class);
                scheduler.<EmailSendHandler>enqueue(id, handler -> handler.handle(reportId));
            }
            case REPORT_SHARE -> {
                var share = json.readValue(payload, ReportShareHandler.Payload.class);
                scheduler.<ReportShareHandler>enqueue(id, handler -> handler.handle(share));
            }
        }
        // A lost acknowledgement is retried with the same UUID, while JobRunr retains the job.
        submissions.markSubmitted(id, Instant.now());
    }
}
