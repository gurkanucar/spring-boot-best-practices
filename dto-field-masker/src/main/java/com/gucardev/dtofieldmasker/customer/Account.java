package com.gucardev.dtofieldmasker.customer;

/** Domain data: always holds the real, unmasked account number. */
public record Account(String accountName, String accountNumber) {
}
