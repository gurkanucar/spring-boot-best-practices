package com.gucardev.reportgenerationlighttaskwithscheduler.report;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Step 3: the "report ready" email (demo: only logs). A real sender passes the idempotency key to
 * the mail provider, because a retried task can send again after the provider accepted the mail.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ReportEmailSender {

    private final ReportRequestRepository reportRequestRepository;
    private final ReportRepository reportRepository;

    public void sendReportReadyEmail(Long requestId) {
        ReportRequest request = reportRequestRepository.findById(requestId).orElseThrow();
        Report report = reportRepository.findByReportRequestId(requestId).orElseThrow();
        String idempotencyKey = "report-ready-email:" + requestId;
        log.info("DEMO: email to {}: report {} is ready (idempotency key {})",
                request.getRequestedBy(), report.getId(), idempotencyKey);
    }
}
