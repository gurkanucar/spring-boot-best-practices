package com.gucardev.resillience4j.timelimiter;

import java.util.concurrent.CompletableFuture;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ShippingController {

    private final ShippingClient client;

    public ShippingController(ShippingClient client) {
        this.client = client;
    }

    /** Spring MVC completes the response when the future completes. */
    @GetMapping("/api/shipping/quote")
    public CompletableFuture<ShippingClient.ShippingQuote> quote(@RequestParam String city) {
        return client.quote(city);
    }
}
