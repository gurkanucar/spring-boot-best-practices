package com.gucardev.restapidesign.course;

import java.time.Instant;

/** Immutable domain value; updates replace the stored copy rather than mutate it. */
public record Course(Long id, String title, String description, int capacity, Status status, long version, Instant createdAt) {

    public enum Status { DRAFT, PUBLISHED, ARCHIVED }
}
