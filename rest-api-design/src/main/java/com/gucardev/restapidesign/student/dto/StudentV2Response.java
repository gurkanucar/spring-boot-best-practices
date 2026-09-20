package com.gucardev.restapidesign.student.dto;

import com.gucardev.restapidesign.student.Student;

/** v2's breaking change: fullName split into firstName/lastName (naive split on first space, demo only). */
public record StudentV2Response(Long id, String firstName, String lastName, String email, Student.Status status) {

    public static StudentV2Response from(Student student) {
        String[] parts = student.fullName().split(" ", 2);
        String firstName = parts[0];
        String lastName = parts.length > 1 ? parts[1] : "";
        return new StudentV2Response(student.id(), firstName, lastName, student.email(), student.status());
    }
}
