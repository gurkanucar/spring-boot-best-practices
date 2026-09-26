package com.gucardev.reportgenerationlighttaskwithjobrunr.report;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** Step 3. Demo: only logs. Replace with your mail provider and pass it the idempotency key. */
@Component
@RequiredArgsConstructor
@Slf4j
public class ReportEmailSender {

    private final ReportRequestRepository reportRequestRepository;
    private final ReportRepository reportRepository;

    public void sendReportReadyEmail(Long requestId) {
        var request = reportRequestRepository.findById(requestId).orElseThrow();
        var report = reportRepository.findByReportRequestId(requestId).orElseThrow();
        // Stable per request, so the provider can drop a duplicate send after a retry.
        String idempotencyKey = "report-ready-email:" + requestId;
        log.info("DEMO: report {} would be emailed to {} (idempotency key {})",
                report.getId(), request.getRequestedBy(), idempotencyKey);
    }
}
