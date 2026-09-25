package com.gucardev.resillience4j.timelimiter;

import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import java.math.BigDecimal;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * TIME LIMITER: the checkout page shows a shipping price. If the courier API is slow we do not
 * make the customer wait; after 1 second we show a flat rate instead.
 *
 * <p>{@code @TimeLimiter} only works on async return types ({@link CompletableFuture}). Timing out
 * the future does NOT stop the HTTP call running underneath, it only stops us waiting for it.
 * That is why the HTTP client also has a read timeout ({@code spring.http.clients.read-timeout}).
 */
@Component
public class ShippingClient {

    private static final Logger log = LoggerFactory.getLogger(ShippingClient.class);
    private static final BigDecimal FLAT_RATE = new BigDecimal("9.99");

    private final RestClient fakeApi;
    private final ExecutorService remoteCallExecutor;

    public ShippingClient(@Lazy RestClient fakeApi, ExecutorService remoteCallExecutor) {
        this.fakeApi = fakeApi;
        this.remoteCallExecutor = remoteCallExecutor;
    }

    @TimeLimiter(name = "shipping", fallbackMethod = "flatRate")
    public CompletableFuture<ShippingQuote> quote(String city) {
        return CompletableFuture.supplyAsync(() -> {
            log.info("Calling courier API for {}", city);
            RemoteQuote remote = fakeApi.get().uri("/shipping/quote?city={city}", city).retrieve().body(RemoteQuote.class);
            return new ShippingQuote(city, remote.price(), remote.days(), remote.carrier());
        }, remoteCallExecutor);
    }

    private CompletableFuture<ShippingQuote> flatRate(String city, Throwable cause) {
        log.info("Using flat shipping rate for {} ({})", city, cause.getClass().getSimpleName());
        return CompletableFuture.completedFuture(new ShippingQuote(city, FLAT_RATE, 3, "flat-rate-fallback"));
    }

    record RemoteQuote(BigDecimal price, int days, String carrier) {
    }

    public record ShippingQuote(String city, BigDecimal price, int days, String source) {
    }
}
