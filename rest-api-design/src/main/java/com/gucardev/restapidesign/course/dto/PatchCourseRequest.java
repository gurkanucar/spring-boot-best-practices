package com.gucardev.restapidesign.course.dto;

/** Only non-null fields are updated; omitted or null fields keep their current value. */
public record PatchCourseRequest(String title, String description, Integer capacity) {
}
