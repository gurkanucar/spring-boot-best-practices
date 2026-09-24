package com.gucardev.ermanytomany.simple.category;

import com.gucardev.ermanytomany.common.error.ConflictException;
import com.gucardev.ermanytomany.common.error.ResourceNotFoundException;
import com.gucardev.ermanytomany.simple.category.dto.CategoryRequest;
import com.gucardev.ermanytomany.simple.category.dto.CategoryResponse;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;

    @Transactional
    public CategoryResponse create(CategoryRequest request) {
        if (categoryRepository.existsByName(request.name())) {
            throw new ConflictException("Category name already in use: " + request.name());
        }
        return CategoryMapper.toResponse(categoryRepository.save(CategoryMapper.toEntity(request)));
    }

    @Transactional(readOnly = true)
    public CategoryResponse get(Long id) {
        return CategoryMapper.toResponse(getEntity(id));
    }

    @Transactional(readOnly = true)
    public Page<CategoryResponse> list(Pageable pageable) {
        return categoryRepository.findAll(pageable).map(CategoryMapper::toResponse);
    }

    @Transactional
    public CategoryResponse update(Long id, CategoryRequest request) {
        Category category = getEntity(id);
        if (categoryRepository.existsByNameAndIdNot(request.name(), id)) {
            throw new ConflictException("Category name already in use: " + request.name());
        }
        category.setName(request.name());
        return CategoryMapper.toResponse(category);
    }

    /**
     * Category is the inverse side, so deleting it does NOT clean the join table: the rows in
     * product_category would still point at it and violate the foreign key. Detach the products
     * first (through the owning side), then delete.
     */
    @Transactional
    public void delete(Long id) {
        Category category = getEntity(id);
        new ArrayList<>(category.getProducts()).forEach(product -> product.removeCategory(category));
        categoryRepository.delete(category);
    }

    @Transactional(readOnly = true)
    public Category getEntity(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found: " + id));
    }

    /** Loads all requested categories in one query; fails with 404 naming the missing ids. */
    @Transactional(readOnly = true)
    public Set<Category> getEntities(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return new HashSet<>();
        }
        Set<Long> requested = new HashSet<>(ids);
        List<Category> found = categoryRepository.findAllById(requested);
        if (found.size() != requested.size()) {
            Set<Long> foundIds = found.stream().map(Category::getId).collect(Collectors.toSet());
            requested.removeAll(foundIds);
            throw new ResourceNotFoundException("Category not found: " + requested);
        }
        return new HashSet<>(found);
    }
}
