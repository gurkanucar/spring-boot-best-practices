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

    private final ReportRequestRepository reportRequestRepository;
    private final ReportRepository reportRepository;
    private final TaskService taskService;
    private final TransactionTemplate transactionTemplate;

    /** Idempotent: a retried task finds the existing report and does nothing. */
    public void generateReport(Long requestId) {
        if (reportRepository.existsByReportRequestId(requestId)) {
            return;
        }
        ReportRequest request = reportRequestRepository.findById(requestId).orElseThrow();
        log.info("Generating {} report for request {}", request.getReportType(), requestId);
        // Demo content. Real rendering (PDF/CSV, upload) also stays outside the transaction.
        String content = "%s report for %s".formatted(request.getReportType(), request.getRequestedBy());

        // The report and its email task commit together.
        transactionTemplate.executeWithoutResult(status -> {
            if (reportRepository.existsByReportRequestId(requestId)) {
                return;
            }
            reportRepository.save(Report.generated(requestId, content));
            taskService.enqueue(BackgroundTask.Type.SEND_REPORT_READY_EMAIL, requestId,
                    "report-ready-email:" + requestId);
        });
    }
}
