package com.gucardev.eronetomany.author.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateAuthorRequest(

        @NotBlank(message = "must not be blank")
        String name) {
}
