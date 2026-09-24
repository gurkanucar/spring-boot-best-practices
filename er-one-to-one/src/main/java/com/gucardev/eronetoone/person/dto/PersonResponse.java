package com.gucardev.eronetoone.person.dto;

import com.gucardev.eronetoone.idcard.dto.IDCardResponse;

public record PersonResponse(Long id, String name, IDCardResponse idCard) {
}
