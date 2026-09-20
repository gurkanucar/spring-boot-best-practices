package com.gucardev.validation.web;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Topic 5 - single-parameter validation ({@code @PathVariable}/{@code @RequestParam}).
 * No class-level {@code @Validated}: that would force AOP validation and change the exception thrown.
 */
@RestController
@RequestMapping("/api/params/users")
public class ParamValidationController {

    @GetMapping("/{id}")
    public Map<String, Object> findById(
            @PathVariable @Min(value = 1, message = "{validation.param.id.min}") Long id) {
        return Map.of("id", id);
    }

    @GetMapping("/search")
    public Map<String, Object> search(
            @RequestParam
            @NotBlank(message = "{validation.user.email.notblank}")
            @Email(message = "{validation.user.email.invalid}")
            @Size(max = 150, message = "{validation.user.email.size}") String email) {
        return Map.of("email", email);
    }
}
