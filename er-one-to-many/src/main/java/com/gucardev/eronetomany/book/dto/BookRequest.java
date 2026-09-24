package com.gucardev.eronetomany.book.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BookRequest(

        @NotBlank(message = "must not be blank")
        String title,

        @NotBlank(message = "must not be blank")
        @Size(max = 20, message = "must be at most 20 characters")
        String isbn) {
}
