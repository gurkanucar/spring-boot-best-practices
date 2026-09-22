package com.gucardev.jackson.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

// What a "date" query parameter actually becomes once it has to reach a LocalDateTime
// column: the half-open [rangeStart, rangeEnd) window covering that whole calendar day.
public record DayRange(LocalDate date, LocalDateTime rangeStart, LocalDateTime rangeEnd) {}
