package com.gucardev.restapiintegration.resilience;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/demo")
public class ResilienceDemoController {

    private final UnreliableApiClient client;
    private final CircuitBreakerClient circuitBreakerClient;

    public ResilienceDemoController(UnreliableApiClient client, CircuitBreakerClient circuitBreakerClient) {
        this.client = client;
        this.circuitBreakerClient = circuitBreakerClient;
    }

    /** {@code style}: none | annotation | template. The remote fails {@code failTimes} times first. */
    @GetMapping("/retry/{style}")
    public Map<String, Object> retry(@PathVariable String style, @RequestParam String key,
                                     @RequestParam(defaultValue = "2") int failTimes) {
        return switch (style) {
            case "none" -> client.flaky(key, failTimes);
            case "annotation" -> client.flakyWithAnnotation(key, failTimes);
            case "template" -> client.flakyWithTemplate(key, failTimes);
            default -> throw new IllegalArgumentException("style must be none, annotation or template");
        };
    }

    /** A remote status that retries cannot fix (4xx) or can (5xx). */
    @GetMapping("/retry/status/{status}")
    public Map<String, Object> retryStatus(@PathVariable int status, @RequestParam String key) {
        return client.statusWithRetry(status, key);
    }

    /** The remote answers after {@code delayMs}; the client gives up after its read timeout. */
    @GetMapping("/timeout")
    public Map<String, Object> timeout(@RequestParam long delayMs) {
        return client.slow(delayMs);
    }

    @GetMapping("/circuit-breaker")
    public Map<String, Object> circuitBreaker(@RequestParam String key, @RequestParam(defaultValue = "1000") int failTimes) {
        return circuitBreakerClient.call(key, failTimes);
    }

    /** How many requests really reached the remote server for {@code key}. */
    @GetMapping("/hits")
    public Map<String, Integer> hits(@RequestParam String key) {
        return Map.of("hits", client.hits(key));
    }
}
