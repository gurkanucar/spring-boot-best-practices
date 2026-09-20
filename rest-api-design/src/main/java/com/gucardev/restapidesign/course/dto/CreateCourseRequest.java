package com.gucardev.restapidesign.course.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record CreateCourseRequest(

        @NotBlank(message = "Title must not be blank")
        String title,

        String description,

        @Min(value = 1, message = "Capacity must be at least 1")
        int capacity) {
}
