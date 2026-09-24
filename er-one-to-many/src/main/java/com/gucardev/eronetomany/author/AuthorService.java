package com.gucardev.eronetomany.author;

import com.gucardev.eronetomany.author.dto.AuthorResponse;
import com.gucardev.eronetomany.author.dto.AuthorSummary;
import com.gucardev.eronetomany.author.dto.CreateAuthorRequest;
import com.gucardev.eronetomany.author.dto.UpdateAuthorRequest;
import com.gucardev.eronetomany.book.BookRepository;
import com.gucardev.eronetomany.book.dto.BookRequest;
import com.gucardev.eronetomany.common.error.ConflictException;
import com.gucardev.eronetomany.common.error.ResourceNotFoundException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthorService {

    private final AuthorRepository authorRepository;
    private final BookRepository bookRepository;

    @Transactional
    public AuthorResponse create(CreateAuthorRequest request) {
        if (request.books() != null) {
            checkIsbnsAreFree(request.books());
        }
        Author saved = authorRepository.save(AuthorMapper.toEntity(request));
        return AuthorMapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public AuthorResponse get(Long id) {
        return AuthorMapper.toResponse(getEntity(id));
    }

    @Transactional(readOnly = true)
    public Page<AuthorResponse> list(Pageable pageable) {
        return authorRepository.findAll(pageable).map(AuthorMapper::toResponse);
    }

    /** Ids-first pagination + fetch join: 3 queries (ids, count, authors with books), paged in SQL. */
    @Transactional(readOnly = true)
    public Page<AuthorResponse> listWithFetchJoin(Pageable pageable) {
        Page<Long> idPage = authorRepository.findPageOfIds(pageable);
        Map<Long, Author> byId = authorRepository.findAllWithBooksByIdIn(idPage.getContent()).stream()
                .collect(Collectors.toMap(Author::getId, Function.identity()));
        // The IN query has no defined order; restore the order the id page was sorted in.
        return idPage.map(id -> AuthorMapper.toResponse(byId.get(id)));
    }

    /** DTO projection: 2 queries (rows, count), no entities and no books loaded. */
    @Transactional(readOnly = true)
    public Page<AuthorSummary> listSummaries(Pageable pageable) {
        return authorRepository.findSummaries(pageable);
    }

    @Transactional
    public AuthorResponse update(Long id, UpdateAuthorRequest request) {
        Author author = getEntity(id);
        author.setName(request.name());
        return AuthorMapper.toResponse(author);
    }

    /** Cascades to the books (Hibernate loads and deletes them one by one). */
    @Transactional
    public void delete(Long id) {
        authorRepository.delete(getEntity(id));
    }

    @Transactional(readOnly = true)
    public Author getEntity(Long id) {
        return authorRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Author not found: " + id));
    }

    @Transactional(readOnly = true)
    public void requireExists(Long id) {
        if (!authorRepository.existsById(id)) {
            throw new ResourceNotFoundException("Author not found: " + id);
        }
    }

    private void checkIsbnsAreFree(List<BookRequest> books) {
        Set<String> seen = new HashSet<>();
        for (BookRequest book : books) {
            if (!seen.add(book.isbn()) || bookRepository.existsByIsbn(book.isbn())) {
                throw new ConflictException("ISBN already in use: " + book.isbn());
            }
        }
    }
}
