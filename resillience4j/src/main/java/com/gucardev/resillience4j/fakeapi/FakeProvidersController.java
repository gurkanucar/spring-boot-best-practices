package com.gucardev.resillience4j.fakeapi;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Stand-ins for third-party services: an FX rate provider, a recommendation engine, a courier,
 * a PDF renderer, an SMS gateway and a payment gateway. In real life each would be another
 * company's API; here they live in the same app so the example runs on its own.
 */
@RestController
@RequestMapping("/fake-api")
public class FakeProvidersController {

    private static final Map<String, BigDecimal> PER_USD = Map.of(
            "USD", BigDecimal.ONE, "EUR", new BigDecimal("0.92"), "GBP", new BigDecimal("0.79"),
            "TRY", new BigDecimal("41.20"), "JPY", new BigDecimal("148.50"));
    private static final int SMS_LIMIT_PER_SECOND = 10;

    private final FakeApiRegistry registry;
    private long smsWindowSecond;
    private int smsInWindow;

    public FakeProvidersController(FakeApiRegistry registry) {
        this.registry = registry;
    }

    @GetMapping("/rates/{from}/{to}")
    public Map<String, Object> rate(@PathVariable String from, @PathVariable String to) {
        FakeApiState state = registry.state(FakeApi.RATES);
        state.simulate();
        BigDecimal fromPerUsd = PER_USD.get(from.toUpperCase());
        BigDecimal toPerUsd = PER_USD.get(to.toUpperCase());
        if (fromPerUsd == null || toPerUsd == null) {
            state.fail(HttpStatus.NOT_FOUND, "unknown currency");
        }
        return Map.of("from", from.toUpperCase(), "to", to.toUpperCase(),
                "rate", toPerUsd.divide(fromPerUsd, 6, RoundingMode.HALF_UP), "fetchedAt", Instant.now());
    }

    @GetMapping("/recommendations/{productId}")
    public Map<String, Object> recommendations(@PathVariable long productId) {
        registry.state(FakeApi.RECOMMENDATIONS).simulate();
        return Map.of("productIds", List.of(productId + 1, productId + 7, productId + 42));
    }

    @GetMapping("/shipping/quote")
    public Map<String, Object> shippingQuote(@RequestParam String city) {
        registry.state(FakeApi.SHIPPING).simulate();
        return Map.of("city", city, "price", new BigDecimal("7.49"), "days", 2, "carrier", "FastCourier");
    }

    @PostMapping("/invoices/{orderId}")
    public Map<String, Object> renderInvoice(@PathVariable String orderId) {
        registry.state(FakeApi.INVOICES).simulate();
        return Map.of("orderId", orderId, "url", "https://files.example.com/invoices/" + orderId + ".pdf");
    }

    /** Like most SMS gateways it enforces its own quota, and answers 429 when a client sends too fast. */
    @PostMapping("/sms")
    public Map<String, Object> sendSms(@RequestBody Map<String, String> sms) {
        FakeApiState state = registry.state(FakeApi.SMS);
        state.recordHit();
        if (!smsQuotaAvailable()) {
            state.fail(HttpStatus.TOO_MANY_REQUESTS, "SMS quota exceeded: max " + SMS_LIMIT_PER_SECOND + "/s");
        }
        state.delay();
        state.maybeFail();
        return Map.of("messageId", UUID.randomUUID().toString(), "to", sms.getOrDefault("to", ""));
    }

    /**
     * Charges once per {@code Idempotency-Key}. The failure is simulated AFTER the charge, like a
     * timeout on the way back: the money moved, but the caller never heard about it. A retry with
     * the same key gets the original charge back instead of charging the customer again.
     */
    @PostMapping("/payments")
    public Map<String, Object> charge(@RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
                                      @RequestBody Map<String, Object> payment) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Idempotency-Key header is required");
        }
        FakeApiState state = registry.state(FakeApi.PAYMENTS);
        state.recordHit();
        state.delay();
        Map<String, Object> charge = registry.charges().computeIfAbsent(idempotencyKey, key -> Map.of(
                "chargeId", "ch_" + UUID.randomUUID().toString().substring(0, 8),
                "amount", payment.get("amount"),
                "currency", payment.get("currency"),
                "chargedAt", Instant.now().toString()));
        state.maybeFail();
        return charge;
    }

    /** The gateway's ledger: how many times customers were really charged. */
    @GetMapping("/payments")
    public Map<String, Object> charges() {
        return Map.of("count", registry.charges().size(), "charges", registry.charges());
    }

    private synchronized boolean smsQuotaAvailable() {
        long second = System.currentTimeMillis() / 1000;
        if (second != smsWindowSecond) {
            smsWindowSecond = second;
            smsInWindow = 0;
        }
        return ++smsInWindow <= SMS_LIMIT_PER_SECOND;
    }
}
