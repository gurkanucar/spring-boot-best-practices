package com.gucardev.dtofieldmasker.customer;

import com.gucardev.dtofieldmasker.customer.dto.CustomerResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class CustomerService {

    private final CustomerRepository customerRepository;

    public List<CustomerResponse> list() {
        return customerRepository.findAll().stream().map(CustomerMapper::toResponse).toList();
    }

    public CustomerResponse get(Long id) {
        return customerRepository.findById(id)
                .map(CustomerMapper::toResponse)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer not found: " + id));
    }
}
