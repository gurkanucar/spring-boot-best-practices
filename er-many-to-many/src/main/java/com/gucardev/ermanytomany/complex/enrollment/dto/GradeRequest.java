package com.gucardev.ermanytomany.complex.enrollment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record GradeRequest(

        @NotBlank(message = "must not be blank")
        @Size(max = 5, message = "must be at most 5 characters")
        String grade) {
}
