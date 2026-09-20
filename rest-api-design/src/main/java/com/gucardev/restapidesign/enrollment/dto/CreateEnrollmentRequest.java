package com.gucardev.restapidesign.enrollment.dto;

import jakarta.validation.constraints.NotNull;

public record CreateEnrollmentRequest(

        @NotNull(message = "studentId must not be null")
        Long studentId,

        @NotNull(message = "courseId must not be null")
        Long courseId) {
}
