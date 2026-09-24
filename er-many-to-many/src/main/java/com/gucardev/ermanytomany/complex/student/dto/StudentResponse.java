package com.gucardev.ermanytomany.complex.student.dto;

/** Enrollments are their own resource (paged, see /students/{id}/enrollments), not embedded here. */
public record StudentResponse(Long id, String name) {
}
