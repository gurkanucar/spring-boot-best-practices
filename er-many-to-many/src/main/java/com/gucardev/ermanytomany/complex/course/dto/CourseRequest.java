package com.gucardev.ermanytomany.complex.course.dto;

import jakarta.validation.constraints.NotBlank;

public record CourseRequest(

        @NotBlank(message = "must not be blank")
        String title) {
}
