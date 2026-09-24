package com.gucardev.eronetoone.person.dto;

import com.gucardev.eronetoone.idcard.dto.IDCardRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

/** {@code idCard} is optional: a person can be created without a card and get one later. */
public record CreatePersonRequest(

        @NotBlank(message = "must not be blank")
        String name,

        @Valid
        IDCardRequest idCard) {
}
