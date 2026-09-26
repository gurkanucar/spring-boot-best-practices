package com.gucardev.reportgenerationlighttaskwithscheduler.report;

import com.gucardev.reportgenerationlighttaskwithscheduler.task.BackgroundTask;
import com.gucardev.reportgenerationlighttaskwithscheduler.task.TaskService;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class ReportService {

    public record ReportRequested(Long reportRequestId, UUID taskId, String reportUrl) {
    }

    public record ReportView(Long reportRequestId, String reportType, String requestedBy, Instant requestedAt,
                             boolean ready, Instant generatedAt, String content) {
    }

    private final ReportRequestRepository reportRequestRepository;
    private final ReportRepository reportRepository;
    private final TaskService taskService;

    /** Saves the request and enqueues GENERATE_REPORT in one transaction. */
    @Transactional
    public ReportRequested requestReport(String reportType, String requestedBy) {
        ReportRequest request = reportRequestRepository.save(ReportRequest.create(reportType, requestedBy));
        UUID taskId = taskService.enqueue(BackgroundTask.Type.GENERATE_REPORT, request.getId(),
                "generate-report:" + request.getId());
        return new ReportRequested(request.getId(), taskId, "/api/reports/" + request.getId());
    }

    @Transactional(readOnly = true)
    public ReportView getReport(Long reportRequestId) {
        ReportRequest request = reportRequestRepository.findById(reportRequestId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Report request " + reportRequestId + " not found"));
        Optional<Report> report = reportRepository.findByReportRequestId(reportRequestId);
        return new ReportView(request.getId(), request.getReportType(), request.getRequestedBy(),
                request.getCreatedAt(), report.isPresent(), report.map(Report::getGeneratedAt).orElse(null),
                report.map(Report::getContent).orElse(null));
    }
}
