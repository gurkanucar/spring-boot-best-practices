package com.gucardev.reportgenerationlighttaskwithjobrunr.report;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jobrunr.jobs.annotations.Job;
import org.jobrunr.scheduling.JobScheduler;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ReportJobs {

    private final ReportRequestRepository requests;
    private final ReportRepository reports;
    private final ReportMailer mailer;
    private final JobScheduler jobScheduler;

    /** Same key, same job id. JobRunr skips an enqueue whose id already exists. */
    public static UUID jobId(String key) {
        return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8));
    }

    @Job(name = "Generate report %0")
    public void generate(Long requestId) {
        UUID reportId = reports.findByReportRequestId(requestId)
                .orElseGet(() -> generateAndSave(requestId))
                .getId();
        // Always enqueue, even if the report already existed: when a previous attempt saved the report
        // but failed here, JobRunr's retry lands here again. The fixed id prevents a second email job.
        jobScheduler.<ReportJobs>enqueue(jobId("ready:" + requestId), j -> j.sendReadyEmail(reportId));
    }

    @Job(name = "Send report-ready email %0")
    public void sendReadyEmail(UUID reportId) {
        var report = reports.findById(reportId).orElseThrow();
        var request = requests.findById(report.getReportRequestId()).orElseThrow();
        mailer.send(request.getRequestedBy(), reportId, jobId("ready:" + request.getId()).toString());
    }

    @Job(name = "Share report %0 with %1")
    public void share(UUID reportId, String recipient) {
        mailer.send(recipient, reportId, jobId("share:" + reportId + ":" + recipient).toString());
    }

    private Report generateAndSave(Long requestId) {
        var request = requests.findById(requestId).orElseThrow();
        // Demo: replace with XLSX rendering/upload. Keep expensive work outside a DB transaction.
        String content = "%s report for %s".formatted(request.getReportType(), request.getRequestedBy());
        try {
            return reports.save(Report.generated(requestId, content));
        } catch (DataIntegrityViolationException e) {
            // A concurrent execution saved it first (unique report_request_id); continue with that one.
            return reports.findByReportRequestId(requestId).orElseThrow();
        }
    }
}
