package com.gucardev.openobserve.order;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    public record CreateOrderRequest(@NotBlank String customer,
                                     @NotNull @DecimalMin("0.01") BigDecimal amount) {
    }

    private final OrderService service;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Order create(@Valid @RequestBody CreateOrderRequest request) {
        return service.create(request.customer(), request.amount());
    }

    @GetMapping("/{id}")
    public Order get(@PathVariable Long id) {
        return service.get(id);
    }

    @GetMapping
    public List<Order> list() {
        return service.list();
    }

    /** GET /api/orders/slow?millis=1500 */
    @GetMapping("/slow")
    public String slow(@RequestParam(defaultValue = "1000") long millis) throws InterruptedException {
        service.slow(Math.min(millis, 10_000));
        return "ok";
    }

    /** Produces a 500 with an ERROR log + failed span, to see errors in OpenObserve. */
    @GetMapping("/fail")
    public String fail() {
        throw new IllegalStateException("Simulated failure");
    }
}
