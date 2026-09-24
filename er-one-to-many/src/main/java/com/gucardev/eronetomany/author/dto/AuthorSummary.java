package com.gucardev.eronetomany.author.dto;

/** Lightweight list row: the book count is computed by the database, no books are loaded. */
public record AuthorSummary(Long id, String name, Long bookCount) {
}
