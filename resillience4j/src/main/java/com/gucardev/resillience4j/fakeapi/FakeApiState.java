package com.gucardev.resillience4j.fakeapi;

import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** How one fake API behaves right now, and how often it was called. Changed at runtime via the admin endpoint. */
public class FakeApiState {

    private final long defaultLatencyMs;
    private final AtomicInteger failNext = new AtomicInteger();
    private final AtomicInteger hits = new AtomicInteger();
    private final AtomicInteger failures = new AtomicInteger();
    private volatile boolean down;
    private volatile long latencyMs;

    FakeApiState(long defaultLatencyMs) {
        this.defaultLatencyMs = defaultLatencyMs;
        this.latencyMs = defaultLatencyMs;
    }

    /** Counts the call, waits the configured latency, then fails if the API is down or has failures queued. */
    public void simulate() {
        recordHit();
        delay();
        maybeFail();
    }

    public void recordHit() {
        hits.incrementAndGet();
    }

    public void delay() {
        if (latencyMs <= 0) {
            return;
        }
        try {
            Thread.sleep(latencyMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public void maybeFail() {
        boolean failQueued = failNext.getAndUpdate(n -> Math.max(0, n - 1)) > 0;
        if (down || failQueued) {
            fail(HttpStatus.SERVICE_UNAVAILABLE, "fake API failure");
        }
    }

    /** Counts a failure and answers with {@code status}. */
    public void fail(HttpStatus status, String reason) {
        failures.incrementAndGet();
        throw new ResponseStatusException(status, reason);
    }

    public void configure(Integer failNext, Boolean down, Long latencyMs) {
        if (failNext != null) {
            this.failNext.set(failNext);
        }
        if (down != null) {
            this.down = down;
        }
        if (latencyMs != null) {
            this.latencyMs = latencyMs;
        }
    }

    void reset() {
        failNext.set(0);
        hits.set(0);
        failures.set(0);
        down = false;
        latencyMs = defaultLatencyMs;
    }

    public Snapshot snapshot() {
        return new Snapshot(hits.get(), failures.get(), failNext.get(), down, latencyMs);
    }

    public record Snapshot(int hits, int failures, int failNext, boolean down, long latencyMs) {
    }
}
