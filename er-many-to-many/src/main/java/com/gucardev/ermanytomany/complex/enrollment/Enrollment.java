package com.gucardev.ermanytomany.complex.enrollment;

import com.gucardev.ermanytomany.complex.course.Course;
import com.gucardev.ermanytomany.complex.student.Student;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

/**
 * The link between Student and Course, modeled as an entity because the relationship carries
 * its own data (grade, enrolledAt). Its primary key is the pair (student_id, course_id), so a
 * student can be enrolled in a course at most once.
 */
@Getter
@Setter
@Entity
@IdClass(EnrollmentId.class)
public class Enrollment {

    @Id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id")
    private Student student;

    @Id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id")
    private Course course;

    @Column(length = 5)
    private String grade;

    @Column(nullable = false)
    private LocalDate enrolledAt;
}
