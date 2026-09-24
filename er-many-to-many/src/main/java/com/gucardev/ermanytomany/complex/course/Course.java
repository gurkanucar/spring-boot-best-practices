package com.gucardev.ermanytomany.complex.course;

import com.gucardev.ermanytomany.complex.enrollment.Enrollment;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;

/** The other side of the many-to-many; see {@code Student} for how enrollments are managed. */
@Getter
@Setter
@Entity
public class Course {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    @BatchSize(size = 50)
    @OneToMany(mappedBy = "course", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<Enrollment> enrollments = new HashSet<>();

    public Set<Enrollment> getEnrollments() {
        return Collections.unmodifiableSet(enrollments);
    }

    /** Course-side hook called by Student.enroll; do not call directly. */
    public void linkEnrollment(Enrollment enrollment) {
        enrollments.add(enrollment);
    }

    /** Course-side hook called by Student.unenroll; do not call directly. */
    public void unlinkEnrollment(Enrollment enrollment) {
        enrollments.remove(enrollment);
    }
}
