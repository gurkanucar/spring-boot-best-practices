package com.gucardev.ratelimitingbucket4j.ratelimit;

import com.gucardev.ratelimitingbucket4j.ratelimit.store.RateLimitStore;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Rate limiting called from code, for rules the filter cannot see: "3 reports per hour per e-mail"
 * needs the e-mail, which only the service knows. Uses the same store as the filter (in memory or
 * Redis), so the limit is shared by every instance in the same way.
 *
 * <pre>{@code
 * rateLimiter.consume("report-per-email", email);   // throws RateLimitExceededException -> 429
 * }</pre>
 */
public class RateLimiter {

    private static final Logger log = LoggerFactory.getLogger(RateLimiter.class);

    private final Map<String, List<RateLimitProperties.Limit>> limits;
    private final RateLimitStore store;

    public RateLimiter(Map<String, List<RateLimitProperties.Limit>> limits, RateLimitStore store) {
        this.limits = limits;
        this.store = store;
    }

    public void consume(String limitName, String key) {
        consume(limitName, key, 1);
    }

    /**
     * Takes {@code tokens} from the bucket {@code <limitName>:<key>} with the limits configured under
     * {@code app.rate-limit.limits.<limitName>}.
     *
     * @throws RateLimitExceededException when the bucket does not have enough tokens
     */
    public void consume(String limitName, String key, long tokens) {
        List<RateLimitProperties.Limit> configured = limits.get(limitName);
        if (configured == null) {
            throw new IllegalArgumentException("No limit configured under app.rate-limit.limits." + limitName);
        }
        RateLimitBucket bucket = new RateLimitBucket(limitName + ":" + key, configured);
        RateLimitStore.Decision decision;
        try {
            decision = store.tryConsume(bucket, tokens);
        } catch (RuntimeException e) {
            // Fail open, like the filter. For abuse-sensitive limits (login, SMS codes) fail closed instead.
            log.warn("Rate limit store unavailable, {} allowed without limit: {}", bucket.key(), e.getMessage());
            return;
        }
        if (!decision.allowed()) {
            throw new RateLimitExceededException(bucket, tokens, decision);
        }
    }
}
