package com.gucardev.reportgenerationlighttaskwithscheduler.report;

import com.gucardev.reportgenerationlighttaskwithscheduler.task.BackgroundTask;
import com.gucardev.reportgenerationlighttaskwithscheduler.task.TaskService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

/**
 * The background work of the report feature. A task can run more than once (retry after a partial
 * success, crash before SUCCEEDED, stuck-task recovery), so every handler is idempotent.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ReportTasks {

    public record SharePayload(UUID reportId, String recipientEmail) {
    }

    private final ReportRequestRepository requests;
    private final ReportRepository reports;
    private final TaskService tasks;
    private final ReportMailer mailer;
    private final TransactionTemplate transaction;
    private final JsonMapper jsonMapper;

    public void run(BackgroundTask.Type type, String payload) {
        switch (type) {
            case REPORT_GENERATION -> generate(jsonMapper.readValue(payload, Long.class));
            case EMAIL_SEND -> sendReadyEmail(jsonMapper.readValue(payload, UUID.class));
            case REPORT_SHARE -> share(jsonMapper.readValue(payload, SharePayload.class));
        }
    }

    /**
     * At most one report per request (checked here, backed by the unique constraint). The report and
     * its "ready" email task commit together, so a saved report never misses its email.
     */
    void generate(Long reportRequestId) {
        if (reports.existsByReportRequestId(reportRequestId)) {
            return;
        }
        ReportRequest request = requests.findById(reportRequestId).orElseThrow();
        log.info("Generating {} report for request {}", request.getReportType(), reportRequestId);
        // Demo content. Real rendering (PDF/CSV, upload) stays outside the transaction below.
        String content = "%s report for %s".formatted(request.getReportType(), request.getRequestedBy());
        transaction.executeWithoutResult(status -> {
            if (reports.existsByReportRequestId(reportRequestId)) {
                return;
            }
            Report report = reports.save(Report.generated(reportRequestId, content));
            tasks.enqueue(BackgroundTask.Type.EMAIL_SEND, report.getId(), "report-ready-email:" + reportRequestId);
        });
    }

    void sendReadyEmail(UUID reportId) {
        Report report = reports.findById(reportId).orElseThrow();
        ReportRequest request = requests.findById(report.getReportRequestId()).orElseThrow();
        mailer.send(request.getRequestedBy(), reportId, "report-ready:" + reportId);
    }

    void share(SharePayload payload) {
        mailer.send(payload.recipientEmail(), payload.reportId(),
                "report-share:" + payload.reportId() + ":" + payload.recipientEmail());
    }
}
