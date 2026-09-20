package com.gucardev.validation.web;

import com.gucardev.validation.user.dto.CrossFieldUserRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Topic 3 - cross-field (class-level) validation. Violations attach to a field
 * via {@code addPropertyNode}, so the response shows a field error, not a global one.
 */
@RestController
@RequestMapping("/api/cross-field/users")
public class CrossFieldValidationController {

    @PostMapping
    public CrossFieldUserRequest create(@Valid @RequestBody CrossFieldUserRequest request) {
        return request;
    }
}
