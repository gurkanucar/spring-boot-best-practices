package com.gucardev.caching.examples;

import static org.assertj.core.api.Assertions.assertThat;

import com.gucardev.caching.CacheManagers;
import com.gucardev.caching.CacheNames;
import com.gucardev.caching.examples.dto.ExpressShipment;
import com.gucardev.caching.examples.dto.FakeOrder;
import com.gucardev.caching.examples.dto.StandardShipment;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;

/**
 * Answers "can the mapper handle a confused nested object" directly, against real Redis:
 * nested records, a List, a Map, BigDecimal, Instant, UUID, Optional, and a polymorphic
 * interface field, all in one payload. Requires {@code docker compose up -d}.
 *
 * <p>Each test uses a key unique to that test run ({@code System.nanoTime()}-suffixed)
 * instead of a fixed key plus a {@code clear()} in {@code @BeforeEach}. Measured directly:
 * a shared fixed key ("standard") plus a clear-then-read pattern raced against this cache
 * manager's pooled connections — clear()'s DEL could still be in flight on one connection
 * while findOrder()'s own cache-lookup GET, on a different pooled connection, read the
 * about-to-be-deleted leftover value from the previous test and treated it as a hit,
 * skipping the real write entirely. Unique keys make that race structurally impossible:
 * there is never a previous value to race against.
 */
@SpringBootTest
class SerializationExamplesTest {

    @Autowired
    private SerializationExamples examples;

    @Autowired
    @Qualifier(CacheManagers.REDIS_30S)
    private CacheManager redisCacheManager30s;

    private void awaitVisible(String key) {
        for (int attempt = 1; attempt <= 20; attempt++) {
            if (redisCacheManager30s.getCache(CacheNames.EXAMPLE_ORDERS).get(key) != null) {
                return;
            }
            sleepBriefly();
        }
        throw new IllegalStateException("Write to key '" + key + "' never became visible after 20 attempts - "
                + "is docker compose up actually running? (see docker-compose.yml)");
    }

    private static void sleepBriefly() {
        try {
            Thread.sleep(25);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Test
    void nestedListsAndMapsAndScalarTypesSurviveTheRoundTrip() {
        String key = "standard-" + System.nanoTime();

        FakeOrder first = examples.findOrder(key);
        awaitVisible(key);
        FakeOrder second = examples.findOrder(key);

        assertThat(second).isEqualTo(first);
        assertThat(second.items()).hasSize(2);
        assertThat(second.metadata()).containsEntry("source", "web");
        assertThat(second.total()).isEqualByComparingTo("44.48");
        assertThat(second.notes()).contains("Leave at front door");
    }

    @Test
    void polymorphicShipmentFieldKeepsItsConcreteTypeAcrossTheRoundTrip() {
        String standardKey = "standard-" + System.nanoTime();
        String expressKey = "express-" + System.nanoTime();

        examples.findOrder(standardKey);
        awaitVisible(standardKey);
        examples.findOrder(expressKey);
        awaitVisible(expressKey);

        // Second call for each key forces an actual Redis GET + deserialize — the first
        // call's return value comes straight from the method, never touching the
        // deserialization path this test exists to prove. This confirms the @class hint
        // correctly told Jackson which concrete class to reconstruct, not just "some
        // object shaped like the Shipment interface".
        FakeOrder standard = examples.findOrder(standardKey);
        FakeOrder express = examples.findOrder(expressKey);

        assertThat(standard.shipment()).isInstanceOf(StandardShipment.class);
        assertThat(express.shipment()).isInstanceOf(ExpressShipment.class);
        assertThat(((StandardShipment) standard.shipment()).estimatedDays()).isEqualTo(5);
        assertThat(((ExpressShipment) express.shipment()).signatureRequired()).isTrue();
    }
}
