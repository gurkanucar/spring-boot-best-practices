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

    @Job(name = "Generate report %0")
    public void generateReport(Long reportId) {
        reportGenerator.generateReport(reportId);
        // Always schedule, even if the report was already ready: when a previous attempt marked it ready
        // but failed here, JobRunr's retry lands here again. The fixed job id prevents a second email job.
        reportJobScheduler.scheduleReportReadyEmail(reportId);
    }

    @Job(name = "Send report-ready email for report %0")
    public void sendReportReadyEmail(Long reportId) {
        reportEmailSender.sendReportReadyEmail(reportId);
    }
}
