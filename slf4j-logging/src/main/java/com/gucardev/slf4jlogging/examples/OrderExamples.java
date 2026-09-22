package com.gucardev.slf4jlogging.examples;

import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderExamples {

    private final AuditExamples audit;

    public enum Mode { SUCCESS, FALLBACK, FAILURE }

    public record Result(long orderId, String status) {
    }

    public Result process(long orderId, Mode mode) {
        log.debug("Starting order processing orderId={} mode={}", orderId, mode);
        if (mode == Mode.FAILURE) {
            // The HTTP exception handler owns this failure's ERROR log; don't log and rethrow here.
            throw new IllegalStateException("Simulated order processor failure");
        }
        String source = "primary";
        if (mode == Mode.FALLBACK) {
            try {
                throw new IOException("Simulated recommendation timeout");
            } catch (IOException ex) {
                // Expected, recoverable situation: actionable context, no repeated stack trace.
                log.warn("Recommendations unavailable; using defaults orderId={}", orderId);
                source = "fallback";
            }
        }
        log.atInfo().addKeyValue("event", "order.processed")
                .addKeyValue("orderId", orderId).addKeyValue("source", source)
                .log("Order processing completed");
        audit.orderProcessed(orderId);
        return new Result(orderId, source);
    }
}
