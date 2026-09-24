package com.gucardev.ermanytomany.complex.enrollment;

import com.gucardev.ermanytomany.common.error.ConflictException;
import com.gucardev.ermanytomany.common.error.ResourceNotFoundException;
import com.gucardev.ermanytomany.complex.course.Course;
import com.gucardev.ermanytomany.complex.course.CourseService;
import com.gucardev.ermanytomany.complex.enrollment.dto.EnrollRequest;
import com.gucardev.ermanytomany.complex.enrollment.dto.EnrollmentResponse;
import com.gucardev.ermanytomany.complex.student.Student;
import com.gucardev.ermanytomany.complex.student.StudentService;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EnrollmentService {

    private final EnrollmentRepository enrollmentRepository;
    private final StudentService studentService;
    private final CourseService courseService;

    @Transactional
    public EnrollmentResponse enroll(Long studentId, EnrollRequest request) {
        Student student = studentService.getEntity(studentId);
        Course course = courseService.getEntity(request.courseId());
        if (enrollmentRepository.existsByIds(studentId, request.courseId())) {
            throw new ConflictException(
                    "Student " + studentId + " is already enrolled in course " + request.courseId());
        }
        Enrollment enrollment = student.enroll(course, request.grade(), LocalDate.now());
        // Persisted by cascade from Student.enrollments; flush now so a constraint violation surfaces here.
        enrollmentRepository.flush();
        return toResponse(enrollment);
    }

    @Transactional(readOnly = true)
    public EnrollmentResponse get(Long studentId, Long courseId) {
        return enrollmentRepository.findResponse(studentId, courseId)
                .orElseThrow(() -> notFound(studentId, courseId));
    }

    @Transactional(readOnly = true)
    public Page<EnrollmentResponse> listByStudent(Long studentId, Pageable pageable) {
        studentService.requireExists(studentId);
        return enrollmentRepository.findResponsesByStudent(studentId, pageable);
    }

    @Transactional(readOnly = true)
    public Page<EnrollmentResponse> listByCourse(Long courseId, Pageable pageable) {
        courseService.requireExists(courseId);
        return enrollmentRepository.findResponsesByCourse(courseId, pageable);
    }

    @Transactional
    public EnrollmentResponse updateGrade(Long studentId, Long courseId, String grade) {
        Enrollment enrollment = enrollmentRepository.findByIds(studentId, courseId)
                .orElseThrow(() -> notFound(studentId, courseId));
        enrollment.setGrade(grade);
        return toResponse(enrollment);
    }

    /** Removes the link only; the student and the course stay. */
    @Transactional
    public void unenroll(Long studentId, Long courseId) {
        Enrollment enrollment = enrollmentRepository.findByIds(studentId, courseId)
                .orElseThrow(() -> notFound(studentId, courseId));
        enrollment.getStudent().unenroll(enrollment);
    }

    private EnrollmentResponse toResponse(Enrollment e) {
        return new EnrollmentResponse(e.getStudent().getId(), e.getStudent().getName(),
                e.getCourse().getId(), e.getCourse().getTitle(), e.getGrade(), e.getEnrolledAt());
    }

    private ResourceNotFoundException notFound(Long studentId, Long courseId) {
        return new ResourceNotFoundException(
                "Student " + studentId + " is not enrolled in course " + courseId);
    }
}
