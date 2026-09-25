package com.gucardev.reportgenerationlighttaskwithjobrunr.tasks.handler;

import com.gucardev.reportgenerationlighttaskwithjobrunr.mail.ReportMailer;
import com.gucardev.reportgenerationlighttaskwithjobrunr.report.ReportRepository;
import com.gucardev.reportgenerationlighttaskwithjobrunr.report.ReportRequestRepository;
import com.gucardev.reportgenerationlighttaskwithjobrunr.tasks.TaskService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jobrunr.JobRunrException;
import org.jobrunr.jobs.annotations.Job;
import org.springframework.stereotype.Component;

/** A separate task so a notification failure does not regenerate the report. */
@Component
@RequiredArgsConstructor
public class EmailSendHandler {

    private final ReportRepository reports;
    private final ReportRequestRepository requests;
    private final ReportMailer mailer;

    @Job(name = "Send report-ready email %0")
    public void handle(UUID reportId) {
        var report = reports.findById(reportId)
                .orElseThrow(() -> new JobRunrException("Report not found: " + reportId, true));
        var request = requests.findById(report.getReportRequestId())
                .orElseThrow(() -> new JobRunrException("Report request not found", true));
        mailer.send(request.getRequestedBy(), report.getId(),
                TaskService.idFor("ready:" + request.getId()).toString());
    }
}
