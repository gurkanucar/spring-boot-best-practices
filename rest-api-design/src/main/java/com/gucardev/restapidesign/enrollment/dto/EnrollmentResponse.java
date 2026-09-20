package com.gucardev.restapidesign.enrollment.dto;

import com.gucardev.restapidesign.enrollment.Enrollment;
import java.time.Instant;

public record EnrollmentResponse(Long id, Long studentId, Long courseId, Enrollment.Status status, Instant enrolledAt, Instant endedAt) {

    public static EnrollmentResponse from(Enrollment enrollment) {
        return new EnrollmentResponse(enrollment.id(), enrollment.studentId(), enrollment.courseId(),
                enrollment.status(), enrollment.enrolledAt(), enrollment.endedAt());
    }
}
