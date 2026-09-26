package com.gucardev.reportgenerationlighttaskwithscheduler.report;

import com.gucardev.reportgenerationlighttaskwithscheduler.report.ReportService.ReportRequested;
import com.gucardev.reportgenerationlighttaskwithscheduler.report.ReportService.ReportView;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    /** {@code requestedBy} stands in for the logged-in user. */
    public record RequestReportBody(String reportType, String requestedBy) {
    }

    private final ReportService reportService;

    /** Step 1: accept the request and answer 202 at once; the UI polls {@code reportUrl}. */
    @PostMapping
    public ResponseEntity<ReportRequested> requestReport(@RequestBody RequestReportBody body) {
        ReportRequested requested = reportService.requestReport(body.reportType(), body.requestedBy());
        return ResponseEntity.accepted().location(URI.create(requested.reportUrl())).body(requested);
    }

    @GetMapping("/{id}")
    public ReportView getReport(@PathVariable Long id) {
        return reportService.getReport(id);
    }
}
