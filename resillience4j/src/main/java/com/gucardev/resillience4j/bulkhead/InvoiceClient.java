package com.gucardev.resillience4j.bulkhead;

import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * BULKHEAD: rendering an invoice PDF takes a second. If 200 users download invoices at once,
 * 200 of our request threads (and connections) wait on this one slow service, and the rest of
 * the shop slows down with it. The bulkhead caps it: at most 2 calls in flight, the rest are
 * rejected at once (503 + Retry-After). One slow dependency can no longer take everything down.
 *
 * <p>This is a semaphore bulkhead (runs on the caller's thread). Resilience4j also has a
 * thread-pool bulkhead ({@code type = THREADPOOL}) that runs calls on a separate, bounded pool.
 */
@Component
public class InvoiceClient {

    private static final Logger log = LoggerFactory.getLogger(InvoiceClient.class);

    private final RestClient fakeApi;

    public InvoiceClient(@Lazy RestClient fakeApi) {
        this.fakeApi = fakeApi;
    }

    @Bulkhead(name = "invoices")
    public Invoice render(String orderId) {
        log.info("Rendering invoice PDF for order {}", orderId);
        return fakeApi.post().uri("/invoices/{orderId}", orderId).retrieve().body(Invoice.class);
    }

    public record Invoice(String orderId, String url) {
    }
}
