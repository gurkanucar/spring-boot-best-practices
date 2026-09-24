package com.gucardev.dtofieldmasker.customer;

import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** In-memory stand-in for a real repository, seeded with two customers. */
@Repository
public class CustomerRepository {

    private final List<Customer> customers = List.of(
            new Customer(1L, "Gurkan", "123456789", List.of(
                    new Account("account1", "123456789012345"),
                    new Account("account2", "1234567890123456"))),
            new Customer(2L, "Mehmet", "987654321", List.of(
                    new Account("account3", "123456789012345678"),
                    new Account("account4", "1234567890123456789"))));

    public List<Customer> findAll() {
        return customers;
    }

    public Optional<Customer> findById(Long id) {
        return customers.stream().filter(c -> c.id().equals(id)).findFirst();
    }
}
