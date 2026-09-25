package com.gucardev.resillience4j.retry;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ExchangeRateController {

    private final ExchangeRateClient client;

    public ExchangeRateController(ExchangeRateClient client) {
        this.client = client;
    }

    @GetMapping("/api/rates/{from}/{to}")
    public ExchangeRateClient.ExchangeRate rate(@PathVariable String from, @PathVariable String to) {
        return client.rate(from, to);
    }
}
