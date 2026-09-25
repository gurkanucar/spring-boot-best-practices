package com.gucardev.resillience4j.ratelimiter;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpClientErrorException;

/** Sends a burst of SMS in parallel, with or without the outbound rate limiter, and reports what happened. */
@RestController
public class SmsCampaignController {

    private final SmsClient client;

    public SmsCampaignController(SmsClient client) {
        this.client = client;
    }

    @PostMapping("/api/sms/campaign")
    public CampaignResult campaign(@RequestParam(defaultValue = "20") int messages,
                                   @RequestParam(defaultValue = "true") boolean throttle) throws InterruptedException {
        long start = System.nanoTime();
        List<Future<String>> results = new ArrayList<>();
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < messages; i++) {
                String to = "+90555000%04d".formatted(i);
                results.add(executor.submit(() -> throttle
                        ? client.send(to, "Flash sale starts now!")
                        : client.sendUnthrottled(to, "Flash sale starts now!")));
            }
        }
        int sent = 0;
        int rejectedByProvider = 0;
        int otherFailures = 0;
        for (Future<String> result : results) {
            try {
                result.get();
                sent++;
            } catch (ExecutionException e) {
                if (e.getCause() instanceof HttpClientErrorException.TooManyRequests) {
                    rejectedByProvider++;
                } else {
                    otherFailures++;
                }
            }
        }
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        return new CampaignResult(throttle, messages, sent, rejectedByProvider, otherFailures, elapsedMs);
    }

    public record CampaignResult(boolean throttled, int messages, int sent, int rejectedByProvider429,
                                 int otherFailures, long elapsedMs) {
    }
}
