package com.gucardev.restapidesign.student.dto;

import com.gucardev.restapidesign.student.Student;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Full replacement body for PUT; every field represents the new complete state. */
public record UpdateStudentRequest(

        @NotBlank(message = "Full name must not be blank")
        String fullName,

        @NotBlank(message = "Email must not be blank")
        @Email(message = "Email must be a valid address")
        String email,

        String phoneNumber,

        @NotNull(message = "Status must not be null")
        Student.Status status) {
}
