package com.gucardev.eronetomany.book;

import com.gucardev.eronetomany.book.dto.BookRequest;
import com.gucardev.eronetomany.book.dto.BookResponse;

public final class BookMapper {

    private BookMapper() {
    }

    public static Book toEntity(BookRequest request) {
        Book book = new Book();
        apply(book, request);
        return book;
    }

    public static void apply(Book book, BookRequest request) {
        book.setTitle(request.title());
        book.setIsbn(request.isbn());
    }

    public static BookResponse toResponse(Book book) {
        // getAuthor() may be a lazy proxy; reading its id does not trigger a query.
        return new BookResponse(book.getId(), book.getTitle(), book.getIsbn(), book.getAuthor().getId());
    }
}
