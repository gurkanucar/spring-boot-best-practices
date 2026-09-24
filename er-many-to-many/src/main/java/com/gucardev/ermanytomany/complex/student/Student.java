package com.gucardev.ermanytomany.complex.student;

import com.gucardev.ermanytomany.complex.course.Course;
import com.gucardev.ermanytomany.complex.enrollment.Enrollment;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import java.time.LocalDate;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;

/** One side of the many-to-many. Two one-to-many relationships to Enrollment replace @ManyToMany. */
@Getter
@Setter
@Entity
public class Student {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    // cascade ALL + orphanRemoval: deleting a student deletes its enrollments, and removing an
    // enrollment from this set deletes its row. Enrollment identity is default (per instance).
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    @BatchSize(size = 50)
    @OneToMany(mappedBy = "student", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<Enrollment> enrollments = new HashSet<>();

    /** Read-only view: callers must go through enroll/unenroll so both sides stay in sync. */
    public Set<Enrollment> getEnrollments() {
        return Collections.unmodifiableSet(enrollments);
    }

    /** Enrolls this student in a course, keeping student.enrollments and course.enrollments in sync. */
    public Enrollment enroll(Course course, String grade, LocalDate enrolledAt) {
        Enrollment enrollment = new Enrollment();
        enrollment.setStudent(this);
        enrollment.setCourse(course);
        enrollment.setGrade(grade);
        enrollment.setEnrolledAt(enrolledAt);

        enrollments.add(enrollment);
        course.linkEnrollment(enrollment);
        return enrollment;
    }

    /** Removes the enrollment from both sides; orphanRemoval deletes the row. */
    public void unenroll(Enrollment enrollment) {
        enrollments.remove(enrollment);
        enrollment.getCourse().unlinkEnrollment(enrollment);
    }
}
