package com.gucardev.reportgenerationlighttaskwithscheduler.report;

import com.gucardev.reportgenerationlighttaskwithscheduler.tasks.entity.BackgroundTask;
import com.gucardev.reportgenerationlighttaskwithscheduler.tasks.entity.TaskType;
import com.gucardev.reportgenerationlighttaskwithscheduler.tasks.handler.ReportShareHandler;
import com.gucardev.reportgenerationlighttaskwithscheduler.tasks.service.TaskService;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
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
    private final int maxRequests;
    private final Duration window;

    public ReportService(ReportRequestRepository requests, ReportRepository reports, TaskService tasks,
                         @Value("${reports.rate-limit.max-requests:3}") int maxRequests,
                         @Value("${reports.rate-limit.window:10m}") Duration window) {
        this.requests = requests;
        this.reports = reports;
        this.tasks = tasks;
        this.maxRequests = maxRequests;
        this.window = window;
    }

    /**
     * The user clicked "export": store the request and its generation task, and return at once.
     * Both rows commit together, so no request is ever left without a task.
     *
     * @throws TooManyReportRequestsException when the user already requested {@code maxRequests}
     *         reports within the last {@code window}
     */
    @Transactional
    public ReportRequested request(String reportType, String requestedBy) {
        checkRateLimit(requestedBy);
        ReportRequest request = requests.save(ReportRequest.create(reportType, requestedBy));
        BackgroundTask task = tasks.enqueue(TaskType.REPORT_GENERATION,
                request.getId(),
                "report-generation:" + request.getId());
        return new ReportRequested(request.getId(), task.getId(), "/api/reports/" + request.getId());
    }

    /**
     * The user clicked "share" on a finished report. Sharing the same report with the same person
     * again returns the existing task instead of sending a second email.
     */
    @Transactional
    public ReportShared share(Long reportRequestId, String recipientEmail) {
        Report report = reports.findByReportRequestId(reportRequestId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                        "Report " + reportRequestId + " is not ready yet"));
        BackgroundTask task = tasks.enqueue(TaskType.REPORT_SHARE,
                new ReportShareHandler.Payload(report.getId(), recipientEmail),
                "report-share:" + report.getId() + ":" + recipientEmail);
        return new ReportShared(task.getId());
    }

    private void checkRateLimit(String requestedBy) {
        requests.lockRequestsOf(requestedBy);
        Instant now = Instant.now();
        Instant since = now.minus(window);
        if (requests.countByRequestedByAndCreatedAtAfter(requestedBy, since) < maxRequests) {
            return;
        }
        // A slot frees up when the oldest request in the window gets older than the window.
        Instant oldest = requests.findFirstByRequestedByAndCreatedAtAfterOrderByCreatedAtAsc(requestedBy, since)
                .orElseThrow().getCreatedAt();
        Duration retryAfter = Duration.between(now, oldest.plus(window));
        throw new TooManyReportRequestsException(maxRequests, window,
                retryAfter.isNegative() ? Duration.ZERO : retryAfter.plusSeconds(1));
    }

    @Transactional(readOnly = true)
    public ReportView get(Long reportRequestId) {
        ReportRequest request = requests.findById(reportRequestId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Report request " + reportRequestId + " not found"));
        var report = reports.findByReportRequestId(reportRequestId);
        return new ReportView(request.getId(), request.getReportType(), request.getRequestedBy(),
                request.getCreatedAt(), report.isPresent(), report.map(Report::getGeneratedAt).orElse(null),
                report.map(Report::getContent).orElse(null));
    }
}
