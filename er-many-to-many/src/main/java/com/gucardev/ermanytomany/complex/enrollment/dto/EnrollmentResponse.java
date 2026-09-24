package com.gucardev.ermanytomany.complex.enrollment.dto;

import java.time.LocalDate;

public record EnrollmentResponse(Long studentId, String studentName, Long courseId, String courseTitle,
                                 String grade, LocalDate enrolledAt) {
}
