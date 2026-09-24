package com.gucardev.ermanytomany.complex.course;

import com.gucardev.ermanytomany.complex.course.dto.CourseRequest;
import com.gucardev.ermanytomany.complex.course.dto.CourseResponse;

public final class CourseMapper {

    private CourseMapper() {
    }

    public static Course toEntity(CourseRequest request) {
        Course course = new Course();
        course.setTitle(request.title());
        return course;
    }

    public static CourseResponse toResponse(Course course) {
        return new CourseResponse(course.getId(), course.getTitle());
    }
}
