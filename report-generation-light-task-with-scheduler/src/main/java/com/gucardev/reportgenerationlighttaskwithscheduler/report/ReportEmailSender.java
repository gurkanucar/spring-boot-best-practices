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

    private final ReportRepository reportRepository;

    public void sendReportReadyEmail(Long reportId) {
        Report report = reportRepository.findById(reportId).orElseThrow();
        String idempotencyKey = "report-ready-email:" + reportId;
        log.info("DEMO: email to {}: report {} is ready (idempotency key {})",
                report.getRequestedBy(), reportId, idempotencyKey);
    }
}
