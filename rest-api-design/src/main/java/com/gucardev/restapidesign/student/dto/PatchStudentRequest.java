package com.gucardev.restapidesign.student.dto;

import com.gucardev.restapidesign.student.Student;

/** Only non-null fields are updated; omitted or null fields keep their current value. */
public record PatchStudentRequest(String fullName, String email, String phoneNumber, Student.Status status) {
}
