package com.gucardev.dtofieldmasker.customer;

import com.gucardev.dtofieldmasker.customer.dto.AccountResponse;
import com.gucardev.dtofieldmasker.customer.dto.CustomerResponse;

public final class CustomerMapper {

    private CustomerMapper() {
    }

    // Copies the real values; masking happens later, when Jackson writes the response DTO.
    public static CustomerResponse toResponse(Customer customer) {
        return new CustomerResponse(customer.name(), customer.idNumber(),
                customer.accounts().stream()
                        .map(a -> new AccountResponse(a.accountName(), a.accountNumber()))
                        .toList());
    }
}
