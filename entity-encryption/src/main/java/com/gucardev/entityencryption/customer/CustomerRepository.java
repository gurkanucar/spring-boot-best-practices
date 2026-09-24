package com.gucardev.entityencryption.customer;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

    /**
     * Never write {@code findByEmail}: the parameter would be encrypted with a random IV and
     * could never equal the stored value. Search by the blind index instead.
     */
    Optional<Customer> findByEmailHash(String emailHash);

    boolean existsByEmailHash(String emailHash);
}
