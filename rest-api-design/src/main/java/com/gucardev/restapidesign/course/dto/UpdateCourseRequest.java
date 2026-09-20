package com.gucardev.restapidesign.course.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/** Status is deliberately absent: state-machine transitions go through /publish and /archive only. */
public record UpdateCourseRequest(

        @NotBlank(message = "Title must not be blank")
        String title,

        String description,

        @Min(value = 1, message = "Capacity must be at least 1")
        int capacity) {
}
