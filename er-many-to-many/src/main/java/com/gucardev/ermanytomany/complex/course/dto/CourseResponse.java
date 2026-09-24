package com.gucardev.ermanytomany.complex.course.dto;

/** The roster is its own resource (paged, see /courses/{id}/enrollments), not embedded here. */
public record CourseResponse(Long id, String title) {
}
