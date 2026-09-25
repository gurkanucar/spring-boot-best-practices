package com.gucardev.reportgenerationlighttaskwithjobrunr.tasks.handler;

import com.gucardev.reportgenerationlighttaskwithjobrunr.mail.ReportMailer;
import com.gucardev.reportgenerationlighttaskwithjobrunr.report.ReportRepository;
import com.gucardev.reportgenerationlighttaskwithjobrunr.tasks.TaskService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jobrunr.JobRunrException;
import org.jobrunr.jobs.annotations.Job;
import org.springframework.stereotype.Component;

/** Example with multiple payload fields: a report and a separate recipient. */
@Component
@RequiredArgsConstructor
public class ReportShareHandler {

    public record Payload(UUID reportId, String recipientEmail) {}

    private final ReportRepository reports;
    private final ReportMailer mailer;

    @Job(name = "Share report %0")
    public void handle(Payload payload) {
        var report = reports.findById(payload.reportId())
                .orElseThrow(() -> new JobRunrException("Report not found: " + payload.reportId(), true));
        // The real mail provider must enforce this stable key across retries.
        mailer.send(payload.recipientEmail(), report.getId(),
                TaskService.idFor("share:" + report.getId() + ":" + payload.recipientEmail()).toString());
    }
}
