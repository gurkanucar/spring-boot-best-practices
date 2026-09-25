package com.gucardev.reportgenerationlighttaskwithjobrunr.report;

import com.gucardev.reportgenerationlighttaskwithjobrunr.tasks.TaskService;
import com.gucardev.reportgenerationlighttaskwithjobrunr.tasks.TaskType;
import com.gucardev.reportgenerationlighttaskwithjobrunr.tasks.handler.ReportShareHandler;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ReportService {

    public record ReportRequested(Long reportRequestId, UUID jobId, String reportUrl) {}
    public record ReportShared(UUID jobId) {}
    public record ReportView(Long reportRequestId, String reportType, String requestedBy, Instant requestedAt,
                             boolean ready, Instant generatedAt, String content, UUID jobId) {}

    private final ReportRequestRepository requests;
    private final ReportRepository reports;
    private final TaskService tasks;
    private final int maxRequests;
    private final Duration window;

    public ReportService(ReportRequestRepository requests, ReportRepository reports,
                         TaskService tasks,
                         @Value("${reports.rate-limit.max-requests:3}") int maxRequests,
                         @Value("${reports.rate-limit.window:10m}") Duration window) {
        this.requests = requests;
        this.reports = reports;
        this.tasks = tasks;
        this.maxRequests = maxRequests;
        this.window = window;
    }

    @Transactional
    public ReportRequested request(String reportType, String requestedBy) {
        String user = requestedBy.trim().toLowerCase(Locale.ROOT);
        checkRateLimit(user);
        var request = requests.save(ReportRequest.create(reportType.trim(), user));
        UUID jobId = tasks.submit(TaskType.REPORT_GENERATION, request.getId(), "generate:" + request.getId());
        return new ReportRequested(request.getId(), jobId, "/api/reports/" + request.getId());
    }

    /** Only the short result + notification-intent commit. Rendering has already finished. */
    @Transactional
    public UUID completeGeneration(Long requestId, String content) {
        requests.findForUpdate(requestId).orElseThrow();
        if (reports.existsByReportRequestId(requestId)) {
            return TaskService.idFor("ready:" + requestId);
        }
        var report = reports.save(Report.generated(requestId, content));
        return tasks.submit(TaskType.EMAIL_SEND, report.getId(), "ready:" + requestId);
    }

    @Transactional
    public ReportShared share(Long requestId, String recipientEmail) {
        // Serialize duplicate shares of this request; no check-then-insert race.
        requests.findForUpdate(requestId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Report request not found"));
        var report = reports.findByReportRequestId(requestId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Report is not ready yet"));
        String recipient = recipientEmail.trim().toLowerCase(Locale.ROOT);
        UUID jobId = tasks.submit(TaskType.REPORT_SHARE, new ReportShareHandler.Payload(report.getId(), recipient),
                "share:" + report.getId() + ":" + recipient);
        return new ReportShared(jobId);
    }

    private void checkRateLimit(String user) {
        requests.lockRequestsOf(user);
        Instant now = Instant.now();
        Instant since = now.minus(window);
        if (requests.countByRequestedByAndCreatedAtAfter(user, since) < maxRequests) {
            return;
        }
        Instant oldest = requests.findFirstByRequestedByAndCreatedAtAfterOrderByCreatedAtAsc(user, since)
                .orElseThrow().getCreatedAt();
        Duration retryAfter = Duration.between(now, oldest.plus(window));
        throw new TooManyReportRequestsException(maxRequests, window,
                retryAfter.isNegative() ? Duration.ZERO : retryAfter.plusSeconds(1));
    }

    @Transactional(readOnly = true)
    public ReportView get(Long requestId) {
        var request = requests.findById(requestId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Report request not found"));
        var report = reports.findByReportRequestId(requestId);
        return new ReportView(requestId, request.getReportType(), request.getRequestedBy(), request.getCreatedAt(),
                report.isPresent(), report.map(Report::getGeneratedAt).orElse(null),
                report.map(Report::getContent).orElse(null), TaskService.idFor("generate:" + requestId));
    }
}
