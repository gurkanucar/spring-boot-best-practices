package com.gucardev.resillience4j.bulkhead;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class InvoiceController {

    private final InvoiceClient client;

    public InvoiceController(InvoiceClient client) {
        this.client = client;
    }

    @PostMapping("/api/orders/{orderId}/invoice")
    public InvoiceClient.Invoice invoice(@PathVariable String orderId) {
        return client.render(orderId);
    }
}
