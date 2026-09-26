package com.gucardev.reportgenerationlighttaskwithjobrunr.jobrunr;

import com.gucardev.reportgenerationlighttaskwithjobrunr.report.ReportEmailSender;
import com.gucardev.reportgenerationlighttaskwithjobrunr.report.ReportGenerator;
import lombok.RequiredArgsConstructor;
import org.jobrunr.jobs.annotations.Job;
import org.springframework.stereotype.Component;

/** JobRunr entry points. They only delegate to plain Spring beans. */
@Component
@RequiredArgsConstructor
public class ReportJobs {

    private final ReportGenerator reportGenerator;
    private final ReportEmailSender reportEmailSender;
    private final ReportJobScheduler reportJobScheduler;

    @Job(name = "Generate report for request %0")
    public void generateReport(Long requestId) {
        reportGenerator.generateReport(requestId);
        // Always schedule, even if the report already existed: when a previous attempt saved the report
        // but failed here, JobRunr's retry lands here again. The fixed job id prevents a second email job.
        reportJobScheduler.scheduleReportReadyEmail(requestId);
    }

    @Job(name = "Send report-ready email for request %0")
    public void sendReportReadyEmail(Long requestId) {
        reportEmailSender.sendReportReadyEmail(requestId);
    }
}
