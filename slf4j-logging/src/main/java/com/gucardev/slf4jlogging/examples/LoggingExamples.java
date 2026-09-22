package com.gucardev.slf4jlogging.examples;

import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** Small, independently runnable examples; the WARN/ERROR in levels() are simulated. */
@Slf4j
@Service
public class LoggingExamples {

    public void levels(long orderId) {
        log.trace("Example TRACE: inspecting calculation steps orderId={}", orderId);
        log.debug("Example DEBUG: preparing order orderId={}", orderId);
        log.info("Example INFO: order accepted orderId={}", orderId);
        log.warn("Example WARN: simulated slow dependency orderId={}", orderId);
        log.error("Example ERROR: simulated processing failure orderId={}", orderId);
    }

    public void parameterized(long orderId) {
        // Prefer placeholders to "order=" + orderId or String.format(...).
        log.info("Order selected orderId={} itemCount={}", orderId, 3);
    }

    public void lazy(long orderId, Supplier<String> expensiveSummary) {
        // {} delays formatting, but ordinary Java method arguments are still evaluated.
        if (log.isDebugEnabled()) {
            log.debug("Guarded summary orderId={} summary={}", orderId, expensiveSummary.get());
        }
        // SLF4J 2: the supplier is not called when DEBUG is disabled. Finish with log().
        log.atDebug().addArgument(orderId).addArgument(expensiveSummary)
                .log("Lazy summary orderId={} summary={}");
    }

    public void structured(long orderId) {
        log.atInfo()
                .addKeyValue("event", "order.previewed")
                .addKeyValue("orderId", orderId)
                .addKeyValue("itemCount", 3)
                .log("Order preview generated");
    }
}
