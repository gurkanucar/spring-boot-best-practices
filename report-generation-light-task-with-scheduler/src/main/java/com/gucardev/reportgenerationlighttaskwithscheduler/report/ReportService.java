package com.gucardev.reportgenerationlighttaskwithscheduler.report;

import com.gucardev.reportgenerationlighttaskwithscheduler.report.ReportTasks.SharePayload;
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

    public record ReportShared(UUID taskId) {
    }

    public record ReportView(Long reportRequestId, String reportType, String requestedBy, Instant requestedAt,
                             boolean ready, Instant generatedAt, String content) {
    }

    private final ReportRequestRepository requests;
    private final ReportRepository reports;
    private final TaskService tasks;

    /** The request and its generation task commit together: no request is left without a task. */
    @Transactional
    public ReportRequested request(String reportType, String requestedBy) {
        ReportRequest request = requests.save(ReportRequest.create(reportType, requestedBy));
        UUID taskId = tasks.enqueue(BackgroundTask.Type.REPORT_GENERATION, request.getId(),
                "report-generation:" + request.getId());
        return new ReportRequested(request.getId(), taskId, "/api/reports/" + request.getId());
    }

    @Transactional(readOnly = true)
    public ReportView get(Long reportRequestId) {
        ReportRequest request = requests.findById(reportRequestId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Report request " + reportRequestId + " not found"));
        Optional<Report> report = reports.findByReportRequestId(reportRequestId);
        return new ReportView(request.getId(), request.getReportType(), request.getRequestedBy(),
                request.getCreatedAt(), report.isPresent(), report.map(Report::getGeneratedAt).orElse(null),
                report.map(Report::getContent).orElse(null));
    }

    /** Sharing the same report with the same person again returns the existing task. */
    @Transactional
    public ReportShared share(Long reportRequestId, String recipientEmail) {
        Report report = reports.findByReportRequestId(reportRequestId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                        "Report " + reportRequestId + " is not ready yet"));
        UUID taskId = tasks.enqueue(BackgroundTask.Type.REPORT_SHARE,
                new SharePayload(report.getId(), recipientEmail),
                "report-share:" + report.getId() + ":" + recipientEmail);
        return new ReportShared(taskId);
    }
}
