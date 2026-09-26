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

    public record ReportRequested(Long reportId, UUID jobId, String reportUrl) {}
    public record ReportView(Long reportId, String reportType, String requestedBy, Report.Status status,
                             Instant requestedAt, Instant generatedAt, String content) {}

    private final ReportRepository reportRepository;
    private final ReportJobScheduler reportJobScheduler;

    public ReportRequested requestReport(String reportType, String requestedBy) {
        Long reportId = reportRepository.save(Report.requested(reportType, requestedBy)).getId();
        // The PENDING report row is committed here. JobRunr OSS enqueue does not join a Spring transaction,
        // so a crash between these two lines leaves a PENDING report without a job (see README).
        UUID jobId = reportJobScheduler.scheduleReportGeneration(reportId);
        return new ReportRequested(reportId, jobId, "/api/reports/" + reportId);
    }

    public ReportView getReport(Long reportId) {
        var report = reportRepository.findById(reportId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Report not found"));
        return new ReportView(report.getId(), report.getReportType(), report.getRequestedBy(), report.getStatus(),
                report.getRequestedAt(), report.getGeneratedAt(), report.getContent());
    }
}
