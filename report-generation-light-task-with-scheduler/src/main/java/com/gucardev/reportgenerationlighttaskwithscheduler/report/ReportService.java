package com.gucardev.reportgenerationlighttaskwithscheduler.report;

import com.gucardev.reportgenerationlighttaskwithscheduler.task.BackgroundTask;
import com.gucardev.reportgenerationlighttaskwithscheduler.task.TaskService;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class ReportService {

    public record ReportRequested(Long reportId, UUID taskId, String reportUrl) {
    }

    public record ReportView(Long reportId, String reportType, String requestedBy, Report.Status status,
                             Instant requestedAt, Instant generatedAt, String content) {
    }

    private final ReportRepository reportRepository;
    private final TaskService taskService;

    /** Saves a PENDING report and enqueues GENERATE_REPORT in one transaction. */
    @Transactional
    public ReportRequested requestReport(String reportType, String requestedBy) {
        Report report = reportRepository.save(Report.requested(reportType, requestedBy));
        Long reportId = report.getId();
        UUID taskId = taskService.enqueue(BackgroundTask.Type.GENERATE_REPORT, reportId,
                "generate-report:" + reportId);
        return new ReportRequested(reportId, taskId, "/api/reports/" + reportId);
    }

    @Transactional(readOnly = true)
    public ReportView getReport(Long reportId) {
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Report " + reportId + " not found"));
        return new ReportView(report.getId(), report.getReportType(), report.getRequestedBy(),
                report.getStatus(), report.getRequestedAt(), report.getGeneratedAt(), report.getContent());
    }
}
