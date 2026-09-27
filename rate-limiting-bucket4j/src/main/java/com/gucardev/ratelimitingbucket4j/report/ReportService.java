package com.gucardev.ratelimitingbucket4j.report;

import com.gucardev.ratelimitingbucket4j.ratelimit.RateLimited;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Report generation is expensive, so each e-mail address may request only a few reports per hour
 * ({@code app.rate-limit.limits.report-per-email}), whatever IP, browser or API key it comes from.
 *
 * <p>The limit is on the service, not the controller: the rule belongs to "generating a report", so
 * it also holds when a report is requested from somewhere else (a scheduled job, a message listener).
 */
@Service
public class ReportService {

    /**
     * @param email already normalised ({@link ReportController#normalize}): the limit is charged before
     *              this method runs, so "Alice@x.com" and "alice@x.com" must arrive as the same key
     */
    @RateLimited(limit = "report-per-email", key = "#email")
    public Map<String, Object> generateMonthly(String email) {
        return Map.of("status", "report queued", "email", email);
    }
}
