package com.gucardev.jackson.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;

// One record per java.time shape you actually run into on a real API: date only, time
// only, date+time with no zone, date+time with an explicit offset, and an absolute
// instant. None of these need jackson-datatype-jsr310 - Jackson 3's core databind
// understands java.time out of the box.
public record EventSchedule(
        // Date only - no time-of-day component at all. Default rendering: "2026-09-22".
        LocalDate eventDate,
        // Time only - no date component. @JsonFormat here trims Jackson's default
        // "14:30:00" (HH:mm:ss, seconds included even when zero) down to "14:30".
        @JsonFormat(pattern = "HH:mm") LocalTime startTime,
        // Date+time, deliberately WITHOUT a zone/offset - e.g. "remind me at this local
        // wall-clock time, wherever the reader happens to be".
        LocalDateTime reminderAt,
        // Date+time WITH an explicit offset - unlike LocalDateTime, this is unambiguous
        // about which real moment it refers to.
        OffsetDateTime publishedAt,
        // Absolute instant, always UTC ("Z") - what createdAt on UserProfile also uses.
        Instant createdAt,
        // ISO-8601 duration ("PT2H30M"), not a plain number of seconds/minutes.
        Duration reminderLeadTime) {}
