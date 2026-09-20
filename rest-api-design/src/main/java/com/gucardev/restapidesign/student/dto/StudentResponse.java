package com.gucardev.restapidesign.student.dto;

import com.gucardev.restapidesign.student.Student;
import java.time.Instant;

public record StudentResponse(Long id, String fullName, String email, String phoneNumber, Student.Status status, Instant createdAt) {

    public static StudentResponse from(Student student) {
        return new StudentResponse(student.id(), student.fullName(), student.email(),
                student.phoneNumber(), student.status(), student.createdAt());
    }
}
