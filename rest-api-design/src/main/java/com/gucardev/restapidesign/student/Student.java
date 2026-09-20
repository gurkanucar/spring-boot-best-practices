package com.gucardev.restapidesign.student;

import java.time.Instant;

/** Immutable domain value; updates replace the stored copy rather than mutate it. */
public record Student(Long id, String fullName, String email, String phoneNumber, Status status, long version, Instant createdAt) {

    public enum Status { ACTIVE, INACTIVE }
}
