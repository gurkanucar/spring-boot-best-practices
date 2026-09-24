package com.gucardev.ermanytomany.complex.student;

import com.gucardev.ermanytomany.complex.student.dto.StudentRequest;
import com.gucardev.ermanytomany.complex.student.dto.StudentResponse;

public final class StudentMapper {

    private StudentMapper() {
    }

    public static Student toEntity(StudentRequest request) {
        Student student = new Student();
        student.setName(request.name());
        return student;
    }

    public static StudentResponse toResponse(Student student) {
        return new StudentResponse(student.getId(), student.getName());
    }
}
