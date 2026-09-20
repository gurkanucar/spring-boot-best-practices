package com.gucardev.restapidesign.course.dto;

import com.gucardev.restapidesign.course.Course;
import java.time.Instant;

public record CourseResponse(Long id, String title, String description, int capacity, Course.Status status, Instant createdAt) {

    public static CourseResponse from(Course course) {
        return new CourseResponse(course.id(), course.title(), course.description(),
                course.capacity(), course.status(), course.createdAt());
    }
}
