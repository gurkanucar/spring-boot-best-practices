package com.gucardev.eronetomany.author;

import com.gucardev.eronetomany.author.dto.AuthorResponse;
import com.gucardev.eronetomany.author.dto.CreateAuthorRequest;
import com.gucardev.eronetomany.book.BookMapper;
import java.util.Comparator;
import com.gucardev.eronetomany.book.Book;

public final class AuthorMapper {

    private AuthorMapper() {
    }

    public static Author toEntity(CreateAuthorRequest request) {
        Author author = new Author();
        author.setName(request.name());
        if (request.books() != null) {
            request.books().forEach(b -> author.addBook(BookMapper.toEntity(b)));
        }
        return author;
    }

    public static AuthorResponse toResponse(Author author) {
        // Sets have no order; sort by id so responses are stable.
        return new AuthorResponse(author.getId(), author.getName(),
                author.getBooks().stream()
                        .sorted(Comparator.comparing(Book::getId))
                        .map(BookMapper::toResponse)
                        .toList());
    }
}
