package com.gucardev.eronetoone.person.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdatePersonRequest(

        @NotBlank(message = "must not be blank")
        String name) {
}
