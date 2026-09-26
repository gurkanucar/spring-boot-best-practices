package com.gucardev.ratelimitingbucket4j.demo;

import com.gucardev.ratelimitingbucket4j.ratelimit.RateLimitFilter;
import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/** Endpoints to try the limits with. The rate limit filter runs before every one of them. */
@RestController
public class DemoController {

    /** Shows how the rate limiter sees the caller. Costs 1 token. */
    @GetMapping("/api/whoami")
    public Map<String, Object> whoAmI(HttpServletRequest request) {
        Object buckets = request.getAttribute(RateLimitFilter.BUCKETS_ATTRIBUTE);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("remoteAddr", request.getRemoteAddr());
        result.put("xForwardedFor", request.getHeader("X-Forwarded-For"));
        result.put("buckets", buckets == null ? List.of("rate limiting disabled") : buckets);
        return result;
    }

    /** A cheap read. Costs 1 token. */
    @GetMapping("/api/products")
    public List<Map<String, Object>> products() {
        return List.of(Map.of("id", 1, "name", "Keyboard"), Map.of("id", 2, "name", "Mouse"));
    }

    /** An expensive operation. Costs 5 tokens ({@code app.rate-limit.costs}). */
    @PostMapping("/api/reports/export")
    public Map<String, Object> export() {
        return Map.of("status", "export started");
    }
}
