package com.gucardev.dtofieldmasker.customer;

import java.util.List;

/** Domain data: always holds the real, unmasked id number. */
public record Customer(Long id, String name, String idNumber, List<Account> accounts) {
}
