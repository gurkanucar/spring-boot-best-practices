package com.gucardev.entityauditing.category;

import com.gucardev.entityauditing.category.dto.CategoryRequest;
import com.gucardev.entityauditing.category.dto.CategoryResponse;
import com.gucardev.entityauditing.common.error.ResourceNotFoundException;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Kept small on purpose: categories exist to show how Envers audits relations. */
@RestController
@RequestMapping("/api/categories")
public class CategoryController {

    private final CategoryRepository repository;

    public CategoryController(CategoryRepository repository) {
        this.repository = repository;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CategoryResponse create(@Valid @RequestBody CategoryRequest request) {
        Category category = repository.save(new Category(request.name()));
        return new CategoryResponse(category.getId(), category.getName());
    }

    @GetMapping
    public List<CategoryResponse> list() {
        return repository.findAll().stream().map(c -> new CategoryResponse(c.getId(), c.getName())).toList();
    }

    @PutMapping("/{id}")
    @Transactional
    public CategoryResponse rename(@PathVariable Long id, @Valid @RequestBody CategoryRequest request) {
        Category category = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category " + id + " not found"));
        category.rename(request.name());
        return new CategoryResponse(category.getId(), category.getName());
    }
}
