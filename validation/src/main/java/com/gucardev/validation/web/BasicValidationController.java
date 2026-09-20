package com.gucardev.validation.web;

import com.gucardev.validation.user.dto.BasicUserRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Topic 1 - basic request validation via {@code @Valid @RequestBody}.
 * Does not persist; echoes back the validated DTO.
 */
@RestController
@RequestMapping("/api/basic/users")
public class BasicValidationController {

    @PostMapping
    public BasicUserRequest create(@Valid @RequestBody BasicUserRequest request) {
        return request;
    }
}
