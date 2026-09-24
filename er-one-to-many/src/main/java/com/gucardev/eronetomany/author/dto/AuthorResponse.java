package com.gucardev.eronetomany.author.dto;

import com.gucardev.eronetomany.book.dto.BookResponse;
import java.util.List;

public record AuthorResponse(Long id, String name, List<BookResponse> books) {
}
