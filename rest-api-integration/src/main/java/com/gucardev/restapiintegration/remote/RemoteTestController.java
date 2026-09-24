package com.gucardev.restapiintegration.remote;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * SIMULATED remote endpoints that misbehave on purpose, so retry, timeout and circuit breaker
 * examples can be shown against real HTTP. Every call is counted per {@code key}, which lets the
 * tests prove how many requests actually reached the server.
 */
@RestController
@RequestMapping("/remote-api")
class RemoteTestController {

    private final Map<String, AtomicInteger> hits = new ConcurrentHashMap<>();

    /** Returns what arrived: method, path, query and headers. Authorization values are hidden. */
    @GetMapping("/echo")
    Map<String, Object> echo(HttpServletRequest request) {
        Map<String, String> headers = new LinkedHashMap<>();
        for (String name : Collections.list(request.getHeaderNames())) {
            String value = request.getHeader(name);
            headers.put(name.toLowerCase(), name.equalsIgnoreCase(HttpHeaders.AUTHORIZATION)
                    ? value.split(" ", 2)[0] + " ***" : value);
        }
        Map<String, Object> echo = new LinkedHashMap<>();
        echo.put("method", request.getMethod());
        echo.put("path", request.getRequestURI());
        echo.put("query", request.getQueryString());
        echo.put("headers", headers);
        return echo;
    }

    /** Fails with 503 for the first {@code failTimes} calls of a key, then succeeds. */
    @GetMapping("/flaky")
    ResponseEntity<Map<String, Object>> flaky(@RequestParam String key, @RequestParam int failTimes) {
        int attempt = hit(key);
        if (attempt <= failTimes) {
            return ResponseEntity.status(503).body(Map.of("error", "temporarily unavailable", "attempt", attempt));
        }
        return ResponseEntity.ok(Map.of("key", key, "attempt", attempt));
    }

    /** Answers after {@code delayMs}; used for the read timeout example. */
    @GetMapping("/slow")
    Map<String, Object> slow(@RequestParam long delayMs) throws InterruptedException {
        Thread.sleep(Math.min(delayMs, 30_000));
        return Map.of("delayMs", delayMs);
    }

    /** Always answers with the given status. */
    @GetMapping("/status/{code}")
    ResponseEntity<Map<String, Object>> status(@PathVariable int code, @RequestParam String key) {
        int attempt = hit(key);
        return ResponseEntity.status(code).body(Map.of("status", code, "attempt", attempt));
    }

    @GetMapping("/hits")
    Map<String, Integer> hits(@RequestParam String key) {
        return Map.of("hits", hits.getOrDefault(key, new AtomicInteger()).get());
    }

    private int hit(String key) {
        return hits.computeIfAbsent(key, k -> new AtomicInteger()).incrementAndGet();
    }
}
