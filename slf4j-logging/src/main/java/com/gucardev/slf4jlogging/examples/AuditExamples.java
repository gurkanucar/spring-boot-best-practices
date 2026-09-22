package com.gucardev.slf4jlogging.examples;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;
import org.springframework.stereotype.Service;

/** A logger category and a marker are labels, not new severity levels. */
@Slf4j(topic = "audit")
@Service
public class AuditExamples {

    private static final Marker AUDIT = MarkerFactory.getMarker("AUDIT");

    public void orderProcessed(long orderId) {
        log.atInfo().addMarker(AUDIT)
                .addKeyValue("event", "order.processed.audit")
                .addKeyValue("actor", "demo-user")
                .addKeyValue("orderId", orderId)
                .log("Demo audit event");
    }
}
