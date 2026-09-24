package com.gucardev.eronetomany.book;

import com.gucardev.eronetomany.book.dto.BookRequest;
import com.gucardev.eronetomany.book.dto.BookResponse;
import com.gucardev.eronetomany.common.KeysetPage;
import jakarta.validation.Valid;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class BookController {

    private final BookService bookService;

    @GetMapping("/api/authors/{authorId}/books")
    public Page<BookResponse> listByAuthor(@PathVariable Long authorId, Pageable pageable) {
        return bookService.listByAuthor(authorId, pageable);
    }

    /** Keyset pagination: {@code ?afterId=<nextCursor of the previous page>&size=}. */
    @GetMapping("/api/authors/{authorId}/books/after")
    public KeysetPage<BookResponse> listAfter(@PathVariable Long authorId,
                                              @RequestParam(defaultValue = "0") Long afterId,
                                              @RequestParam(defaultValue = "20") int size) {
        return bookService.listAfter(authorId, afterId, size);
    }

    @PostMapping("/api/authors/{authorId}/books")
    public ResponseEntity<BookResponse> add(@PathVariable Long authorId,
                                            @Valid @RequestBody BookRequest request) {
        BookResponse created = bookService.add(authorId, request);
        return ResponseEntity.created(URI.create("/api/books/" + created.id())).body(created);
    }

    @PutMapping("/api/authors/{authorId}/books/{bookId}")
    public BookResponse update(@PathVariable Long authorId, @PathVariable Long bookId,
                               @Valid @RequestBody BookRequest request) {
        return bookService.update(authorId, bookId, request);
    }

    @DeleteMapping("/api/authors/{authorId}/books/{bookId}")
    public ResponseEntity<Void> remove(@PathVariable Long authorId, @PathVariable Long bookId) {
        bookService.remove(authorId, bookId);
        return ResponseEntity.noContent().build();
    }

    /** Navigating from the owning (many) side: book to its author id. */
    @GetMapping("/api/books/{bookId}")
    public BookResponse get(@PathVariable Long bookId) {
        return bookService.get(bookId);
    }
}
