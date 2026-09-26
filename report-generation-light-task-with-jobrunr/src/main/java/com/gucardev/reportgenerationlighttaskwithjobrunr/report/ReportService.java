package com.gucardev.reportgenerationlighttaskwithjobrunr.report;

import com.gucardev.reportgenerationlighttaskwithjobrunr.jobrunr.ReportJobScheduler;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class ReportService {

    public record ReportRequested(Long reportRequestId, UUID jobId, String reportUrl) {}
    public record ReportView(Long reportRequestId, String reportType, String requestedBy, Instant requestedAt,
                             boolean ready, Instant generatedAt, String content) {}

    private final ReportRequestRepository reportRequestRepository;
    private final ReportRepository reportRepository;
    private final ReportJobScheduler reportJobScheduler;

    public ReportRequested requestReport(String reportType, String requestedBy) {
        Long requestId = reportRequestRepository.save(ReportRequest.create(reportType, requestedBy)).getId();
        // The request row is committed here. JobRunr OSS enqueue does not join a Spring transaction,
        // so a crash between these two lines leaves a request without a job (see README).
        UUID jobId = reportJobScheduler.scheduleReportGeneration(requestId);
        return new ReportRequested(requestId, jobId, "/api/reports/" + requestId);
    }

    public ReportView getReport(Long requestId) {
        var request = reportRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Report request not found"));
        var report = reportRepository.findByReportRequestId(requestId);
        return new ReportView(requestId, request.getReportType(), request.getRequestedBy(), request.getCreatedAt(),
                report.isPresent(), report.map(Report::getGeneratedAt).orElse(null),
                report.map(Report::getContent).orElse(null));
    }
}
