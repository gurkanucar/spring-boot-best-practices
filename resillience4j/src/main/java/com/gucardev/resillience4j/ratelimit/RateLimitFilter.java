package com.gucardev.resillience4j.ratelimit;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.json.JsonMapper;

/**
 * Charges every {@code /api/**} request against its buckets. Answers 429 with {@code Retry-After}
 * when one is empty, and tells well-behaved clients how much is left before that happens.
 */
public class RateLimitFilter extends OncePerRequestFilter {

    /** Request attribute with the buckets of the current request (shown by /api/whoami). */
    public static final String BUCKETS_ATTRIBUTE = RateLimitFilter.class.getName() + ".buckets";

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    private final RateLimitPolicy policy;
    private final RateLimitStore store;
    private final JsonMapper jsonMapper;

    public RateLimitFilter(RateLimitPolicy policy, RateLimitStore store, JsonMapper jsonMapper) {
        this.policy = policy;
        this.store = store;
        this.jsonMapper = jsonMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        List<RateLimitBucket> buckets;
        try {
            buckets = policy.bucketsFor(request, response);
        } catch (RateLimitPolicy.InvalidApiKeyException e) {
            writeError(response, HttpStatus.UNAUTHORIZED, e.getMessage(), Map.of());
            return;
        }
        request.setAttribute(BUCKETS_ATTRIBUTE, buckets);

        Checked tightest = null;
        for (RateLimitBucket bucket : buckets) {
            RateLimitStore.Decision decision;
            try {
                decision = store.tryConsume(bucket);
            } catch (RuntimeException e) {
                // Fail open: if the counter store (Redis) is down, serving requests without limits
                // is better than rejecting everyone. Fail closed instead if abuse is the bigger risk.
                log.warn("Rate limit store unavailable, request allowed without limit: {}", e.getMessage());
                continue;
            }
            if (!decision.allowed()) {
                long retryAfterSeconds = Math.max(1, (decision.retryAfter().toMillis() + 999) / 1000);
                response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfterSeconds));
                setLimitHeaders(response, bucket, decision);
                writeError(response, HttpStatus.TOO_MANY_REQUESTS, "Rate limit exceeded for " + bucket.key(),
                        Map.of("bucket", bucket.key(), "limit", decision.limit(),
                                "period", bucket.limit().period().toString(), "retryAfterSeconds", retryAfterSeconds));
                return;
            }
            if (tightest == null || decision.remaining() < tightest.decision().remaining()) {
                tightest = new Checked(bucket, decision);
            }
        }
        if (tightest != null) {
            setLimitHeaders(response, tightest.bucket(), tightest.decision());
        }
        chain.doFilter(request, response);
    }

    /**
     * {@code RateLimit-*} follow the IETF draft; many APIs use {@code X-RateLimit-*}. The bucket
     * header is for this demo only, a real API would not reveal how it identifies callers.
     */
    private static void setLimitHeaders(HttpServletResponse response, RateLimitBucket bucket, RateLimitStore.Decision decision) {
        response.setHeader("RateLimit-Limit", String.valueOf(decision.limit()));
        response.setHeader("RateLimit-Remaining", String.valueOf(decision.remaining()));
        response.setHeader("X-RateLimit-Bucket", bucket.key());
    }

    private void writeError(HttpServletResponse response, HttpStatus status, String detail, Map<String, Object> extra)
            throws IOException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", status.value());
        body.put("title", status.getReasonPhrase());
        body.put("detail", detail);
        body.putAll(extra);
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        jsonMapper.writeValue(response.getOutputStream(), body);
    }

    private record Checked(RateLimitBucket bucket, RateLimitStore.Decision decision) {
    }
}
