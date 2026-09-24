package com.gucardev.entityencryption.customer;

import com.gucardev.entityencryption.common.error.ConflictException;
import com.gucardev.entityencryption.common.error.ResourceNotFoundException;
import com.gucardev.entityencryption.crypto.BlindIndex;
import com.gucardev.entityencryption.customer.dto.CustomerRequest;
import com.gucardev.entityencryption.customer.dto.CustomerResponse;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class CustomerService {

    private final CustomerRepository repository;
    private final BlindIndex blindIndex;

    public CustomerService(CustomerRepository repository, BlindIndex blindIndex) {
        this.repository = repository;
        this.blindIndex = blindIndex;
    }

    public CustomerResponse create(CustomerRequest request) {
        String emailHash = blindIndex.of(request.email());
        if (repository.existsByEmailHash(emailHash)) {
            throw new ConflictException("A customer with this email already exists");
        }
        Customer customer = new Customer(request.name(), request.email(), emailHash,
                request.nationalId(), request.phone());
        return toResponse(repository.save(customer));
    }

    @Transactional(readOnly = true)
    public CustomerResponse get(Long id) {
        return toResponse(find(id));
    }

    @Transactional(readOnly = true)
    public List<CustomerResponse> list() {
        return repository.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public CustomerResponse findByEmail(String email) {
        return repository.findByEmailHash(blindIndex.of(email))
                .map(this::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("No customer with this email"));
    }

    public CustomerResponse update(Long id, CustomerRequest request) {
        Customer customer = find(id);
        String emailHash = blindIndex.of(request.email());
        repository.findByEmailHash(emailHash)
                .filter(other -> !other.getId().equals(id))
                .ifPresent(other -> {
                    throw new ConflictException("A customer with this email already exists");
                });
        customer.update(request.name(), request.email(), emailHash, request.nationalId(), request.phone());
        return toResponse(customer);
    }

    public void delete(Long id) {
        repository.delete(find(id));
    }

    private Customer find(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Customer " + id + " not found"));
    }

    private CustomerResponse toResponse(Customer c) {
        return new CustomerResponse(c.getId(), c.getName(), c.getEmail(), c.getNationalId(), c.getPhone());
    }
}
