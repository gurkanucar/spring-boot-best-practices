package com.gucardev.resillience4j.ratelimit;

import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Shows how the rate limiter sees the caller. Handy while trying the scenarios in the README. */
@RestController
public class WhoAmIController {

    @GetMapping("/api/whoami")
    public Map<String, Object> whoAmI(HttpServletRequest request) {
        Object buckets = request.getAttribute(RateLimitFilter.BUCKETS_ATTRIBUTE);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("remoteAddr", request.getRemoteAddr());
        result.put("xForwardedFor", request.getHeader("X-Forwarded-For"));
        result.put("buckets", buckets == null ? List.of("rate limiting disabled") : buckets);
        return result;
    }
}
