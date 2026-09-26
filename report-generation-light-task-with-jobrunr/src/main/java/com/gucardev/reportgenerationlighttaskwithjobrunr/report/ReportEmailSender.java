package com.gucardev.reportgenerationlighttaskwithjobrunr.report;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** Step 3. Demo: only logs. Replace with your mail provider and pass it the idempotency key. */
@Component
@RequiredArgsConstructor
@Slf4j
public class ReportEmailSender {

    private final ReportRepository reportRepository;

    public void sendReportReadyEmail(Long reportId) {
        var report = reportRepository.findById(reportId).orElseThrow();
        // Stable per report, so the provider can drop a duplicate send after a retry.
        String idempotencyKey = "report-ready-email:" + reportId;
        log.info("DEMO: report {} would be emailed to {} (idempotency key {})",
                reportId, report.getRequestedBy(), idempotencyKey);
    }
}
