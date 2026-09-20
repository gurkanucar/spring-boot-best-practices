package com.gucardev.restapidesign.enrollment;

import java.time.Instant;

/** Immutable; has no PUT/PATCH/DELETE — its lifecycle moves only through action endpoints. */
public record Enrollment(Long id, Long studentId, Long courseId, Status status, Instant enrolledAt, Instant endedAt) {

    public enum Status { ACTIVE, COMPLETED, DROPPED }
}
