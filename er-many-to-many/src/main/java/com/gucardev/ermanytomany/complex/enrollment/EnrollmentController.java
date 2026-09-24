package com.gucardev.ermanytomany.complex.enrollment;

import com.gucardev.ermanytomany.complex.enrollment.dto.EnrollRequest;
import com.gucardev.ermanytomany.complex.enrollment.dto.EnrollmentResponse;
import com.gucardev.ermanytomany.complex.enrollment.dto.GradeRequest;
import jakarta.validation.Valid;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class EnrollmentController {

    private final EnrollmentService enrollmentService;

    @PostMapping("/api/complex/students/{studentId}/enrollments")
    public ResponseEntity<EnrollmentResponse> enroll(@PathVariable Long studentId,
                                                     @Valid @RequestBody EnrollRequest request) {
        EnrollmentResponse created = enrollmentService.enroll(studentId, request);
        return ResponseEntity.created(URI.create(
                "/api/complex/students/" + studentId + "/enrollments/" + created.courseId())).body(created);
    }

    @GetMapping("/api/complex/students/{studentId}/enrollments")
    public Page<EnrollmentResponse> listByStudent(@PathVariable Long studentId, Pageable pageable) {
        return enrollmentService.listByStudent(studentId, pageable);
    }

    @GetMapping("/api/complex/students/{studentId}/enrollments/{courseId}")
    public EnrollmentResponse get(@PathVariable Long studentId, @PathVariable Long courseId) {
        return enrollmentService.get(studentId, courseId);
    }

    @PutMapping("/api/complex/students/{studentId}/enrollments/{courseId}")
    public EnrollmentResponse updateGrade(@PathVariable Long studentId, @PathVariable Long courseId,
                                          @Valid @RequestBody GradeRequest request) {
        return enrollmentService.updateGrade(studentId, courseId, request.grade());
    }

    @DeleteMapping("/api/complex/students/{studentId}/enrollments/{courseId}")
    public ResponseEntity<Void> unenroll(@PathVariable Long studentId, @PathVariable Long courseId) {
        enrollmentService.unenroll(studentId, courseId);
        return ResponseEntity.noContent().build();
    }

    /** The same link seen from the other side: the roster of a course. */
    @GetMapping("/api/complex/courses/{courseId}/enrollments")
    public Page<EnrollmentResponse> listByCourse(@PathVariable Long courseId, Pageable pageable) {
        return enrollmentService.listByCourse(courseId, pageable);
    }
}
