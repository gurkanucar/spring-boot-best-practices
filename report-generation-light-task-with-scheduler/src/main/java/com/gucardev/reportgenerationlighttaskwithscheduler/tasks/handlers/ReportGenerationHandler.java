package com.gucardev.reportgenerationlighttaskwithscheduler.tasks.handlers;

import com.gucardev.reportgenerationlighttaskwithscheduler.report.Report;
import com.gucardev.reportgenerationlighttaskwithscheduler.report.ReportRepository;
import com.gucardev.reportgenerationlighttaskwithscheduler.report.ReportRequest;
import com.gucardev.reportgenerationlighttaskwithscheduler.report.ReportRequestRepository;
import com.gucardev.reportgenerationlighttaskwithscheduler.tasks.NonRetryableTaskException;
import com.gucardev.reportgenerationlighttaskwithscheduler.tasks.Payloads;
import com.gucardev.reportgenerationlighttaskwithscheduler.tasks.TaskHandler;
import com.gucardev.reportgenerationlighttaskwithscheduler.tasks.TaskService;
import com.gucardev.reportgenerationlighttaskwithscheduler.tasks.TaskType;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/**
 * Payload: {@code {"reportRequestId": "<uuid>"}}.
 *
 * <p><b>Idempotent:</b> if a report for the request already exists, nothing is generated again.
 * The unique constraint on {@code report.report_request_id} backs this up when two executions race.
 * The "report ready" email is enqueued in the same transaction as the report, with an
 * idempotency key, so a rerun cannot queue a second email either.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ReportGenerationHandler implements TaskHandler {

    private final ReportRequestRepository requests;
    private final ReportRepository reports;
    private final TaskService tasks;

    @Override
    public TaskType type() {
        return TaskType.REPORT_GENERATION;
    }

    @Override
    public void handle(JsonNode payload) {
        UUID reportRequestId = Payloads.requireUuid(payload, "reportRequestId");
        if (reports.existsByReportRequestId(reportRequestId)) {
            log.info("Report for request {} already exists, skipping", reportRequestId);
            return;
        }
        ReportRequest request = requests.findById(reportRequestId)
                .orElseThrow(() -> new NonRetryableTaskException("Report request " + reportRequestId + " does not exist"));

        log.info("Generating {} report for request {}", request.getReportType(), reportRequestId);
        // TODO: query the data, render the file (PDF/CSV), upload it to object storage and store its key.
        String content = "%s report for %s".formatted(request.getReportType(), request.getRequestedBy());
        reports.save(Report.generated(reportRequestId, content));

        tasks.enqueue(TaskType.EMAIL_SEND, Map.of("reportRequestId", reportRequestId.toString()),
                "report-ready-email:" + reportRequestId);
    }
}
