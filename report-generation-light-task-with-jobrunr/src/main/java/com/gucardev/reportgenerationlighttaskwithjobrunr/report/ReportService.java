package com.gucardev.reportgenerationlighttaskwithjobrunr.report;

import static com.gucardev.reportgenerationlighttaskwithjobrunr.report.ReportJobs.jobId;

import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jobrunr.scheduling.JobScheduler;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class ReportService {

    public record ReportRequested(Long reportRequestId, UUID jobId, String reportUrl) {}
    public record ReportShared(UUID jobId) {}
    public record ReportView(Long reportRequestId, String reportType, String requestedBy, Instant requestedAt,
                             boolean ready, Instant generatedAt, String content) {}

    private final ReportRequestRepository requests;
    private final ReportRepository reports;
    private final JobScheduler jobScheduler;

    public ReportRequested request(String reportType, String requestedBy) {
        Long id = requests.save(ReportRequest.create(reportType, requestedBy)).getId();
        // The request row is committed here. JobRunr OSS enqueue does not join a Spring transaction,
        // so a crash between these two lines leaves a request without a job (see README).
        UUID jobId = jobScheduler.<ReportJobs>enqueue(jobId("generate:" + id), j -> j.generate(id)).asUUID();
        return new ReportRequested(id, jobId, "/api/reports/" + id);
    }

    public ReportView get(Long requestId) {
        var request = requests.findById(requestId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Report request not found"));
        var report = reports.findByReportRequestId(requestId);
        return new ReportView(requestId, request.getReportType(), request.getRequestedBy(), request.getCreatedAt(),
                report.isPresent(), report.map(Report::getGeneratedAt).orElse(null),
                report.map(Report::getContent).orElse(null));
    }

    public ReportShared share(Long requestId, String recipient) {
        if (!requests.existsById(requestId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Report request not found");
        }
        UUID reportId = reports.findByReportRequestId(requestId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Report is not ready yet"))
                .getId();
        UUID jobId = jobScheduler.<ReportJobs>enqueue(jobId("share:" + reportId + ":" + recipient),
                j -> j.share(reportId, recipient)).asUUID();
        return new ReportShared(jobId);
    }
}
