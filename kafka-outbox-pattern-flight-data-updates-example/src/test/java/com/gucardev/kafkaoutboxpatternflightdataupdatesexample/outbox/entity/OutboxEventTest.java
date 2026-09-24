package com.gucardev.kafkaoutboxpatternflightdataupdatesexample.outbox.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OutboxEventTest {

    @Test
    void failedAttemptsBackOffExponentiallyUpToTheMaximum() {
        OutboxEvent event = OutboxEvent.pending(UUID.randomUUID(), "Flight", "TK1971-20260924-IST",
                "FLIGHT_DELAYED", "flight-updates", "{}");

        for (long expectedSeconds : new long[] {1, 2, 4, 8, 10, 10}) {
            Instant before = Instant.now();
            event.recordFailedAttempt("Broker unavailable", Duration.ofSeconds(1), Duration.ofSeconds(10));
            Instant after = Instant.now();
            assertThat(event.getNextAttemptAt()).isBetween(before.plusSeconds(expectedSeconds),
                    after.plusSeconds(expectedSeconds));
        }
        assertThat(event.getAttempts()).isEqualTo(6);
        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PENDING);
    }
}
