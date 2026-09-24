package com.gucardev.eronetoone.person;

import com.gucardev.eronetoone.person.dto.CreatePersonRequest;
import com.gucardev.eronetoone.person.dto.PersonResponse;
import com.gucardev.eronetoone.person.dto.UpdatePersonRequest;
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
@RequestMapping("/api/persons")
@RequiredArgsConstructor
public class PersonController {

    private final PersonService personService;

    @PostMapping
    public ResponseEntity<PersonResponse> create(@Valid @RequestBody CreatePersonRequest request) {
        PersonResponse created = personService.create(request);
        return ResponseEntity.created(URI.create("/api/persons/" + created.id())).body(created);
    }

    @GetMapping("/{id}")
    public PersonResponse get(@PathVariable Long id) {
        return personService.get(id);
    }

    @GetMapping
    public Page<PersonResponse> list(Pageable pageable) {
        return personService.list(pageable);
    }

    @PutMapping("/{id}")
    public PersonResponse update(@PathVariable Long id, @Valid @RequestBody UpdatePersonRequest request) {
        return personService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        personService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
