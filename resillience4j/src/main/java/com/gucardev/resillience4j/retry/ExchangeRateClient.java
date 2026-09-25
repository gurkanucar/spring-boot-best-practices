package com.gucardev.resillience4j.retry;

import io.github.resilience4j.retry.annotation.Retry;
import java.math.BigDecimal;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * RETRY: the FX rate provider sometimes answers 503 for a moment. Trying again a little later
 * usually works, so a short failure never reaches our users.
 *
 * <p>Instance {@code exchangeRates} in application.yaml: 4 attempts, exponential backoff with
 * jitter, only 5xx and I/O errors. A 4xx (unknown currency) fails immediately.
 * Only retry calls that are safe to repeat: this is a GET, so it is.
 */
@Component
public class ExchangeRateClient {

    private static final Logger log = LoggerFactory.getLogger(ExchangeRateClient.class);

    private final RestClient fakeApi;

    public ExchangeRateClient(@Lazy RestClient fakeApi) {
        this.fakeApi = fakeApi;
    }

    @Retry(name = "exchangeRates")
    public ExchangeRate rate(String from, String to) {
        log.info("Calling FX rate provider {} -> {}", from, to);
        return fakeApi.get().uri("/rates/{from}/{to}", from, to).retrieve().body(ExchangeRate.class);
    }

    public record ExchangeRate(String from, String to, BigDecimal rate, Instant fetchedAt) {
    }
}
