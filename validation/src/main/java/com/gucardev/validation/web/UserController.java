package com.gucardev.validation.web;

import com.gucardev.validation.user.UserService;
import com.gucardev.validation.user.dto.CreateUserRequest;
import com.gucardev.validation.user.dto.UnsafeCreateUserRequest;
import com.gucardev.validation.user.dto.UserResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Topic 7 - real persistence flow with three layers stacked: {@code /users} gets a 400 from
 * {@code @UniqueEmail}, {@code /users/unsafe} gets a 409 from the DB constraint.
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse create(@Valid @RequestBody CreateUserRequest request) {
        return userService.create(request);
    }

    @PostMapping("/unsafe")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse createUnsafe(@Valid @RequestBody UnsafeCreateUserRequest request) {
        return userService.createUnsafe(request);
    }

    @GetMapping("/{id}")
    public UserResponse findById(@PathVariable Long id) {
        return userService.findById(id);
    }
}
