package com.gucardev.reportgenerationlighttaskwithjobrunr.report;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ReportGenerator {

    private final ReportRequestRepository reportRequestRepository;
    private final ReportRepository reportRepository;

    /** Step 2. Idempotent: returns the existing report if this request was already generated. */
    public Report generateReport(Long requestId) {
        return reportRepository.findByReportRequestId(requestId)
                .orElseGet(() -> generateAndSave(requestId));
    }

    private Report generateAndSave(Long requestId) {
        var request = reportRequestRepository.findById(requestId).orElseThrow();
        // Demo: replace with XLSX rendering/upload. Keep expensive work outside a DB transaction.
        String content = "%s report for %s".formatted(request.getReportType(), request.getRequestedBy());
        try {
            return reportRepository.save(Report.generated(requestId, content));
        } catch (DataIntegrityViolationException e) {
            // A concurrent execution saved it first (unique report_request_id); continue with that one.
            return reportRepository.findByReportRequestId(requestId).orElseThrow();
        }
    }
}
