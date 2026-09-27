package com.gucardev.ratelimitingbucket4j.report;

import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * The e-mail comes from a header only to keep the demo small. Anyone can send any header, so in a
 * real application take it from the authenticated user (e.g. the JWT), never from the request.
 */
@RestController
public class ReportController {

    public static final String EMAIL_HEADER = "X-User-Email";

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    /** Also passes the {@code RateLimitFilter} (1 token), like every other {@code /api/**} request. */
    @PostMapping("/api/reports/monthly")
    public Map<String, Object> monthly(@RequestHeader(EMAIL_HEADER) String email) {
        return reportService.generateMonthly(email);
    }
}
