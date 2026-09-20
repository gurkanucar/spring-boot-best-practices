package com.gucardev.validation.user.dto;

import com.gucardev.validation.user.Role;

public record UserResponse(Long id, String fullName, String email, int age, Role role) {
}
