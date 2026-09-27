package com.gucardev.ratelimitingbucket4j.report;

import com.gucardev.ratelimitingbucket4j.ratelimit.RateLimiter;
import java.util.Locale;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/**
 * Report generation is expensive, so each e-mail address may request only a few reports per hour
 * ({@code app.rate-limit.limits.report-per-email}), whatever IP, browser or API key it comes from.
 *
 * <p>The check is here, not in the controller: the rule belongs to "generating a report", so it
 * also holds when a report is requested from somewhere else (a scheduled job, a message listener).
 */
@Service
public class ReportService {

    static final String LIMIT = "report-per-email";

    /** Absent when {@code app.rate-limit.enabled=false}. */
    private final ObjectProvider<RateLimiter> rateLimiter;

    public ReportService(ObjectProvider<RateLimiter> rateLimiter) {
        this.rateLimiter = rateLimiter;
    }

    public Map<String, Object> generateMonthly(String email) {
        // Normalised, or "Alice@x.com" and "alice@x.com " would be two buckets for the same person.
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        rateLimiter.ifAvailable(limiter -> limiter.consume(LIMIT, normalized));
        return Map.of("status", "report queued", "email", normalized);
    }
}
