package com.gucardev.caching.examples.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Deliberately "confused": nested records, a List, a Map, BigDecimal, Instant, UUID,
 * an Optional, and a polymorphic {@link Shipment} field — everything RedisCacheConfig's
 * comment lists as needing default typing to survive the round trip. {@code Optional} as
 * a record component is not something to copy into real code (Effective Java specifically
 * advises against it); it's here only because it's a genuine Jackson serialization edge
 * case worth proving works.
 */
public record FakeOrder(
        UUID id,
        String customerName,
        List<OrderLine> items,
        Map<String, String> metadata,
        BigDecimal total,
        OrderStatus status,
        Shipment shipment,
        Optional<String> notes,
        Instant placedAt) {

    public static FakeOrder fakeStandard() {
        return new FakeOrder(
                UUID.randomUUID(),
                "Jane Smith",
                List.of(new OrderLine("SKU-1", 2, new BigDecimal("19.99")), new OrderLine("SKU-2", 1, new BigDecimal("4.50"))),
                Map.of("source", "web", "campaign", "autumn-sale"),
                new BigDecimal("44.48"),
                OrderStatus.PLACED,
                new StandardShipment("PostalService", 5),
                Optional.of("Leave at front door"),
                Instant.now());
    }

    public static FakeOrder fakeExpress() {
        return new FakeOrder(
                UUID.randomUUID(),
                "Bob Jones",
                List.of(new OrderLine("SKU-3", 1, new BigDecimal("99.00"))),
                Map.of("source", "mobile-app"),
                new BigDecimal("99.00"),
                OrderStatus.PLACED,
                new ExpressShipment("Courier", true),
                Optional.empty(),
                Instant.now());
    }
}
