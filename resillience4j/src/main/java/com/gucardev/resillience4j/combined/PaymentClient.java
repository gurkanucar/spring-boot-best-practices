package com.gucardev.resillience4j.combined;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import java.math.BigDecimal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * RETRY + CIRCUIT BREAKER on a call that is NOT naturally safe to repeat.
 *
 * <p>Order: Resilience4j applies its aspects as
 * {@code Retry( CircuitBreaker( RateLimiter( TimeLimiter( Bulkhead( call )))))}, no matter the
 * order of the annotations in the source. So every retry attempt is one call through the circuit
 * breaker, and a burst of failed attempts can open it. When it is open, retrying is pointless:
 * {@code CallNotPermittedException} is in the retry's {@code ignore-exceptions}.
 * The order can be changed with {@code resilience4j.retry.retry-aspect-order} etc.
 *
 * <p>Idempotency: a payment request can fail AFTER the gateway charged the card (the response
 * was lost). A blind retry would charge twice. So the caller creates one {@code Idempotency-Key}
 * per payment, and every attempt sends the same key; the gateway charges once per key.
 * The key must be created outside the retried method, otherwise each attempt gets a new one.
 *
 * <p>No fallback here on purpose: pretending a payment worked is worse than a clear error.
 */
@Component
public class PaymentClient {

    private static final Logger log = LoggerFactory.getLogger(PaymentClient.class);

    private final RestClient fakeApi;

    public PaymentClient(@Lazy RestClient fakeApi) {
        this.fakeApi = fakeApi;
    }

    @Retry(name = "payments")
    @CircuitBreaker(name = "payments")
    public Charge charge(String idempotencyKey, BigDecimal amount, String currency) {
        log.info("Charging {} {} (Idempotency-Key {})", amount, currency, idempotencyKey);
        return fakeApi.post().uri("/payments")
                .header("Idempotency-Key", idempotencyKey)
                .body(new ChargeRequest(amount, currency))
                .retrieve()
                .body(Charge.class);
    }

    record ChargeRequest(BigDecimal amount, String currency) {
    }

    public record Charge(String chargeId, BigDecimal amount, String currency, String chargedAt) {
    }
}
