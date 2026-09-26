package com.gucardev.reportgenerationlighttaskwithjobrunr.report;

import com.gucardev.reportgenerationlighttaskwithjobrunr.report.ReportService.ReportRequested;
import com.gucardev.reportgenerationlighttaskwithjobrunr.report.ReportService.ReportView;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    // Demo identity. In production use the authenticated principal and enforce ownership/tenant access.
    public record ReportRequestBody(String reportType, String requestedBy) {}

    private final ReportService reportService;

    /** Step 1: accept the request. The report and the email are produced by background jobs. */
    @PostMapping
    public ResponseEntity<ReportRequested> requestReport(@RequestBody ReportRequestBody body) {
        var result = reportService.requestReport(body.reportType(), body.requestedBy());
        return ResponseEntity.accepted().location(URI.create(result.reportUrl())).body(result);
    }

    /** Poll until {@code status} is READY. */
    @GetMapping("/{reportId}")
    public ReportView getReport(@PathVariable Long reportId) {
        return reportService.getReport(reportId);
    }
}
