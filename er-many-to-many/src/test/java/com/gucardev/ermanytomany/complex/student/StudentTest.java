package com.gucardev.ermanytomany.complex.student;

import static org.assertj.core.api.Assertions.assertThat;

import com.gucardev.ermanytomany.complex.course.Course;
import com.gucardev.ermanytomany.complex.enrollment.Enrollment;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class StudentTest {

    @Test
    void enrollLinksStudentAndCourseInBothDirections() {
        Student student = new Student();
        Course course = new Course();

        Enrollment enrollment = student.enroll(course, "AA", LocalDate.of(2026, 1, 1));

        assertThat(enrollment.getStudent()).isSameAs(student);
        assertThat(enrollment.getCourse()).isSameAs(course);
        assertThat(enrollment.getGrade()).isEqualTo("AA");
        assertThat(student.getEnrollments()).containsExactly(enrollment);
        assertThat(course.getEnrollments()).containsExactly(enrollment);
    }

    @Test
    void unenrollRemovesTheEnrollmentFromBothSides() {
        Student student = new Student();
        Course course = new Course();
        Enrollment enrollment = student.enroll(course, null, LocalDate.now());

        student.unenroll(enrollment);

        assertThat(student.getEnrollments()).isEmpty();
        assertThat(course.getEnrollments()).isEmpty();
    }
}
