package com.gucardev.reportgenerationlighttaskwithscheduler.tasks;

import java.util.UUID;
import tools.jackson.databind.JsonNode;

/** Reading payload fields. A missing or malformed field will not fix itself, so it is non-retryable. */
public final class Payloads {

    private Payloads() {
    }

    public static UUID requireUuid(JsonNode payload, String field) {
        try {
            return UUID.fromString(payload.path(field).asString());
        } catch (RuntimeException e) {
            throw new NonRetryableTaskException("Payload field '" + field + "' must be a UUID", e);
        }
    }
}
