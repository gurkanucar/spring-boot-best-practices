package com.gucardev.ermanytomany.complex.student.dto;

import jakarta.validation.constraints.NotBlank;

public record StudentRequest(

        @NotBlank(message = "must not be blank")
        String name) {
}
