package com.gucardev.restapidesign.enrollment;

import com.gucardev.restapidesign.common.PageResponse;
import com.gucardev.restapidesign.common.PageSupport;
import com.gucardev.restapidesign.course.Course;
import com.gucardev.restapidesign.course.CourseStore;
import com.gucardev.restapidesign.enrollment.dto.CreateEnrollmentRequest;
import com.gucardev.restapidesign.enrollment.dto.EnrollmentResponse;
import com.gucardev.restapidesign.error.ConflictException;
import com.gucardev.restapidesign.error.ResourceNotFoundException;
import com.gucardev.restapidesign.student.StudentStore;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Topic 4 — the many-to-many relationship as its own resource. No PUT/PATCH/DELETE:
 * its lifecycle moves only through create (enroll) and the complete/drop actions.
 */
@RestController
@RequestMapping("/api/v1/enrollments")
public class EnrollmentController {

    private final EnrollmentStore enrollments;
    private final StudentStore students;
    private final CourseStore courses;

    public EnrollmentController(EnrollmentStore enrollments, StudentStore students, CourseStore courses) {
        this.enrollments = enrollments;
        this.students = students;
        this.courses = courses;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EnrollmentResponse create(@Valid @RequestBody CreateEnrollmentRequest request) {
        // Shared with parent deletion; capacity and duplicate checks also precede one atomic insert.
        synchronized (enrollments) {
            return createEnrollment(request);
        }
    }

    private EnrollmentResponse createEnrollment(CreateEnrollmentRequest request) {
        if (!students.existsById(request.studentId())) {
            throw new ResourceNotFoundException("Student " + request.studentId() + " not found");
        }
        Course course = courses.findByIdOrThrow(request.courseId());
        if (course.status() != Course.Status.PUBLISHED) {
            throw new ConflictException("Course is not accepting enrollments: status is " + course.status() + ", must be PUBLISHED");
        }
        if (enrollments.hasActiveEnrollment(request.studentId(), request.courseId())) {
            throw new ConflictException("Student " + request.studentId() + " is already enrolled in course " + request.courseId());
        }
        if (enrollments.countActiveByCourse(request.courseId()) >= course.capacity()) {
            throw new ConflictException("Course " + request.courseId() + " is at capacity");
        }
        Enrollment enrollment = enrollments.create(request.studentId(), request.courseId());
        return EnrollmentResponse.from(enrollment);
    }

    @GetMapping
    public PageResponse<EnrollmentResponse> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Long studentId,
            @RequestParam(required = false) Long courseId,
            @RequestParam(required = false) Enrollment.Status status) {

        List<Enrollment> all = enrollments.findAll();
        if (studentId != null) {
            all = all.stream().filter(e -> e.studentId().equals(studentId)).toList();
        }
        if (courseId != null) {
            all = all.stream().filter(e -> e.courseId().equals(courseId)).toList();
        }
        if (status != null) {
            all = all.stream().filter(e -> e.status() == status).toList();
        }
        List<EnrollmentResponse> responses = all.stream().map(EnrollmentResponse::from).toList();
        return PageSupport.paginate(responses, page, size);
    }

    @GetMapping("/{id}")
    public EnrollmentResponse getById(@PathVariable Long id) {
        return EnrollmentResponse.from(enrollments.findByIdOrThrow(id));
    }

    @PostMapping("/{id}/complete")
    public EnrollmentResponse complete(@PathVariable Long id) {
        Enrollment current = requireActive(id);
        Enrollment updated = new Enrollment(current.id(), current.studentId(), current.courseId(),
                Enrollment.Status.COMPLETED, current.enrolledAt(), Instant.now());
        return EnrollmentResponse.from(enrollments.replace(id, updated));
    }

    @PostMapping("/{id}/drop")
    public EnrollmentResponse drop(@PathVariable Long id) {
        Enrollment current = requireActive(id);
        Enrollment updated = new Enrollment(current.id(), current.studentId(), current.courseId(),
                Enrollment.Status.DROPPED, current.enrolledAt(), Instant.now());
        return EnrollmentResponse.from(enrollments.replace(id, updated));
    }

    private Enrollment requireActive(Long id) {
        Enrollment current = enrollments.findByIdOrThrow(id);
        if (current.status() != Enrollment.Status.ACTIVE) {
            throw new ConflictException("Enrollment " + id + " is not ACTIVE (currently " + current.status() + ")");
        }
        return current;
    }
}
