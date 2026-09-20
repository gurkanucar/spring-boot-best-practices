package com.gucardev.restapidesign.student.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record CreateStudentRequest(

        @NotBlank(message = "Full name must not be blank")
        String fullName,

        @NotBlank(message = "Email must not be blank")
        @Email(message = "Email must be a valid address")
        String email) {
}
