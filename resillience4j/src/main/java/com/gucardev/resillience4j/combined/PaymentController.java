package com.gucardev.resillience4j.combined;

import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PaymentController {

    private final PaymentClient client;

    public PaymentController(PaymentClient client) {
        this.client = client;
    }

    /**
     * Our own clients may send an {@code Idempotency-Key} too (so their retries are safe); if they
     * do not, one key is created here, once, before any retry happens.
     */
    @PostMapping("/api/payments")
    public PaymentClient.Charge pay(@RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
                                    @RequestBody PaymentRequest request) {
        String key = idempotencyKey != null ? idempotencyKey : UUID.randomUUID().toString();
        return client.charge(key, request.amount(), request.currency());
    }

    public record PaymentRequest(BigDecimal amount, String currency) {
    }
}
