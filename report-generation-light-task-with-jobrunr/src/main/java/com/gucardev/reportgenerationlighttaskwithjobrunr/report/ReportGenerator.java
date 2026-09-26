package com.gucardev.reportgenerationlighttaskwithjobrunr.report;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ReportGenerator {

    private final ReportRepository reportRepository;

    /** Step 2. Idempotent: does nothing if the report is already ready. */
    public void generateReport(Long reportId) {
        var report = reportRepository.findById(reportId).orElseThrow();
        if (report.getStatus() == Report.Status.READY) {
            return;
        }
        // Demo: replace with XLSX rendering/upload. Keep expensive work outside a DB transaction.
        String content = "%s report for %s".formatted(report.getReportType(), report.getRequestedBy());
        report.markReady(content);
        // Plain save: two concurrent runs would both write the same content, so the last write wins harmlessly.
        reportRepository.save(report);
    }
}
