package com.gucardev.ermanytomany.complex.enrollment.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** {@code grade} is optional: a student is enrolled first and graded later. */
public record EnrollRequest(

        @NotNull(message = "must not be null")
        Long courseId,

        @Size(max = 5, message = "must be at most 5 characters")
        String grade) {
}
