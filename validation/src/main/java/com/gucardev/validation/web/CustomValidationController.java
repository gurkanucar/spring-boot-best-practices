package com.gucardev.validation.web;

import com.gucardev.validation.user.dto.CustomUserRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Topic 2 - custom field-level constraints. Violations surface through the same
 * {@code MethodArgumentNotValidException} to 400 + {@code errors[]} path as built-in constraints.
 */
@RestController
@RequestMapping("/api/custom/users")
public class CustomValidationController {

    @PostMapping
    public CustomUserRequest create(@Valid @RequestBody CustomUserRequest request) {
        return request;
    }
}
