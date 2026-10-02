package com.gucardev.openobserve.order;

import io.micrometer.observation.annotation.Observed;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;

@Slf4j
// Class level: every public method becomes a span plus one shared timer, order_service_*,
// tagged with class and method. Needs management.observations.annotations.enabled.
@Observed(name = "order.service")
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository repository;

    public Order create(String customer, BigDecimal amount) {
        Order order = new Order();
        order.setCustomer(customer);
        order.setAmount(amount);
        Order saved = repository.save(order);
        log.info("Order created id={} customer={}", saved.getId(), customer);
        return saved;
    }

    public Order get(Long id) {
        return repository.findById(id).orElseThrow(() -> {
            log.warn("Order not found id={}", id);
            return new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found");
        });
    }

    public List<Order> list() {
        return repository.findAll();
    }

    /** Demo helper: slow work shows up as a long span and in the latency histogram. */
    public void slow(long millis) throws InterruptedException {
        log.info("Slow operation started millis={}", millis);
        Thread.sleep(millis);
    }
}
