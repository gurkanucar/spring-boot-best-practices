package com.gucardev.ermanytomany.complex.enrollment;

import com.gucardev.ermanytomany.complex.enrollment.dto.EnrollmentResponse;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EnrollmentRepository extends JpaRepository<Enrollment, EnrollmentId> {

    // The three DTO queries below select only the columns the response needs. No Student, Course
    // or Enrollment entity is loaded, so there is no lazy loading and no N+1 to worry about.
    String SELECT_RESPONSE = """
            select new com.gucardev.ermanytomany.complex.enrollment.dto.EnrollmentResponse(
                s.id, s.name, c.id, c.title, e.grade, e.enrolledAt)
            from Enrollment e join e.student s join e.course c
            """;

    @Query("select e from Enrollment e where e.student.id = :studentId and e.course.id = :courseId")
    Optional<Enrollment> findByIds(@Param("studentId") Long studentId, @Param("courseId") Long courseId);

    @Query("select count(e) > 0 from Enrollment e where e.student.id = :studentId and e.course.id = :courseId")
    boolean existsByIds(@Param("studentId") Long studentId, @Param("courseId") Long courseId);

    @Query(SELECT_RESPONSE + " where s.id = :studentId and c.id = :courseId")
    Optional<EnrollmentResponse> findResponse(@Param("studentId") Long studentId, @Param("courseId") Long courseId);

    @Query(value = SELECT_RESPONSE + " where s.id = :studentId",
            countQuery = "select count(e) from Enrollment e where e.student.id = :studentId")
    Page<EnrollmentResponse> findResponsesByStudent(@Param("studentId") Long studentId, Pageable pageable);

    @Query(value = SELECT_RESPONSE + " where c.id = :courseId",
            countQuery = "select count(e) from Enrollment e where e.course.id = :courseId")
    Page<EnrollmentResponse> findResponsesByCourse(@Param("courseId") Long courseId, Pageable pageable);
}
