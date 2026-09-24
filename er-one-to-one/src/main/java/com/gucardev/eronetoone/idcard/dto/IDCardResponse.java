package com.gucardev.eronetoone.idcard.dto;

import java.time.LocalDate;

public record IDCardResponse(Long id, String cardNumber, LocalDate expiryDate, Long personId) {
}
