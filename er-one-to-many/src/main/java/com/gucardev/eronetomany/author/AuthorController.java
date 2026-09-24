package com.gucardev.eronetomany.author;

import com.gucardev.eronetomany.author.dto.AuthorResponse;
import com.gucardev.eronetomany.author.dto.AuthorSummary;
import com.gucardev.eronetomany.author.dto.CreateAuthorRequest;
import com.gucardev.eronetomany.author.dto.UpdateAuthorRequest;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/authors")
@RequiredArgsConstructor
public class AuthorController {

    private final AuthorService authorService;

    @PostMapping
    public ResponseEntity<AuthorResponse> create(@Valid @RequestBody CreateAuthorRequest request) {
        AuthorResponse created = authorService.create(request);
        return ResponseEntity.created(URI.create("/api/authors/" + created.id())).body(created);
    }

    @GetMapping("/{id}")
    public AuthorResponse get(@PathVariable Long id) {
        return authorService.get(id);
    }

    @GetMapping
    public Page<AuthorResponse> list(Pageable pageable) {
        return authorService.list(pageable);
    }

    @GetMapping("/fetch-join")
    public Page<AuthorResponse> listWithFetchJoin(Pageable pageable) {
        return authorService.listWithFetchJoin(pageable);
    }

    @GetMapping("/summaries")
    public Page<AuthorSummary> listSummaries(Pageable pageable) {
        return authorService.listSummaries(pageable);
    }

    @PutMapping("/{id}")
    public AuthorResponse update(@PathVariable Long id, @Valid @RequestBody UpdateAuthorRequest request) {
        return authorService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        authorService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
