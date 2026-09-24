package com.gucardev.eronetomany.book;

import com.gucardev.eronetomany.author.Author;
import com.gucardev.eronetomany.author.AuthorService;
import com.gucardev.eronetomany.book.dto.BookRequest;
import com.gucardev.eronetomany.book.dto.BookResponse;
import com.gucardev.eronetomany.common.error.ConflictException;
import com.gucardev.eronetomany.common.error.ResourceNotFoundException;
import com.gucardev.eronetomany.common.KeysetPage;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BookService {

    private final BookRepository bookRepository;
    private final AuthorService authorService;

    @Transactional(readOnly = true)
    public Page<BookResponse> listByAuthor(Long authorId, Pageable pageable) {
        authorService.requireExists(authorId);
        return bookRepository.findByAuthorId(authorId, pageable).map(BookMapper::toResponse);
    }

    /** Cursor-based paging: pass the previous page's nextCursor as afterId (0 for the first page). */
    @Transactional(readOnly = true)
    public KeysetPage<BookResponse> listAfter(Long authorId, Long afterId, int size) {
        authorService.requireExists(authorId);
        int pageSize = Math.min(Math.max(size, 1), 100);
        // Ask for one extra row: if it comes back there is a next page, without a count query.
        List<BookResponse> rows = bookRepository.findNextPage(authorId, afterId, PageRequest.of(0, pageSize + 1));
        boolean hasNext = rows.size() > pageSize;
        List<BookResponse> items = hasNext ? rows.subList(0, pageSize) : rows;
        return new KeysetPage<>(items, hasNext ? items.getLast().id() : null);
    }

    @Transactional(readOnly = true)
    public BookResponse get(Long bookId) {
        return bookRepository.findById(bookId)
                .map(BookMapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Book not found: " + bookId));
    }

    @Transactional
    public BookResponse add(Long authorId, BookRequest request) {
        Author author = authorService.getEntity(authorId);
        if (bookRepository.existsByIsbn(request.isbn())) {
            throw new ConflictException("ISBN already in use: " + request.isbn());
        }
        Book book = BookMapper.toEntity(request);
        author.addBook(book);
        // Flush so the IDENTITY id is assigned and any constraint violation surfaces here.
        bookRepository.saveAndFlush(book);
        return BookMapper.toResponse(book);
    }

    @Transactional
    public BookResponse update(Long authorId, Long bookId, BookRequest request) {
        Book book = findOwned(authorId, bookId);
        if (bookRepository.existsByIsbnAndIdNot(request.isbn(), bookId)) {
            throw new ConflictException("ISBN already in use: " + request.isbn());
        }
        BookMapper.apply(book, request);
        return BookMapper.toResponse(book);
    }

    /** Removes the book from the author's collection; orphanRemoval deletes the row. */
    @Transactional
    public void remove(Long authorId, Long bookId) {
        Author author = authorService.getEntity(authorId);
        Book book = author.getBooks().stream()
                .filter(b -> b.getId().equals(bookId))
                .findFirst()
                .orElseThrow(() -> bookNotFound(authorId, bookId));
        author.removeBook(book);
    }

    private Book findOwned(Long authorId, Long bookId) {
        return bookRepository.findByIdAndAuthorId(bookId, authorId)
                .orElseThrow(() -> bookNotFound(authorId, bookId));
    }

    private ResourceNotFoundException bookNotFound(Long authorId, Long bookId) {
        return new ResourceNotFoundException("Book " + bookId + " not found for author " + authorId);
    }
}
