package com.gucardev.reportgenerationlighttaskwithscheduler.report;

import com.gucardev.reportgenerationlighttaskwithscheduler.task.BackgroundTask;
import com.gucardev.reportgenerationlighttaskwithscheduler.task.TaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/** Step 2: generates the report, then enqueues the "report ready" email. */
@Component
@RequiredArgsConstructor
@Slf4j
public class ReportGenerator {

    private final ReportRepository reportRepository;
    private final TaskService taskService;
    private final TransactionTemplate transactionTemplate;

    /** Idempotent: a retried task finds the report READY and does nothing. */
    public void generateReport(Long reportId) {
        Report report = reportRepository.findById(reportId).orElseThrow();
        if (report.getStatus() == Report.Status.READY) {
            return;
        }
        log.info("Generating {} report {}", report.getReportType(), reportId);
        // Demo content. Real rendering (PDF/CSV, upload) also stays outside the transaction.
        String content = "%s report for %s".formatted(report.getReportType(), report.getRequestedBy());

        // READY and the email task commit together; the status is re-checked on a fresh load, and
        // the email's idempotency key stops a duplicate email if two runs ever race past it.
        transactionTemplate.executeWithoutResult(status -> {
            Report current = reportRepository.findById(reportId).orElseThrow();
            if (current.getStatus() == Report.Status.READY) {
                return;
            }
            current.markReady(content);
            taskService.enqueue(BackgroundTask.Type.SEND_REPORT_READY_EMAIL, reportId,
                    "report-ready-email:" + reportId);
        });
    }
}
