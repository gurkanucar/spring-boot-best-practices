package com.gucardev.restapidesign.student;

import com.gucardev.restapidesign.common.ETagSupport;
import com.gucardev.restapidesign.common.PageResponse;
import com.gucardev.restapidesign.common.PageSupport;
import com.gucardev.restapidesign.common.SortSupport;
import com.gucardev.restapidesign.course.CourseStore;
import com.gucardev.restapidesign.course.dto.CourseResponse;
import com.gucardev.restapidesign.enrollment.Enrollment;
import com.gucardev.restapidesign.enrollment.EnrollmentStore;
import com.gucardev.restapidesign.error.PreconditionFailedException;
import com.gucardev.restapidesign.error.ConflictException;
import com.gucardev.restapidesign.error.PreconditionRequiredException;
import com.gucardev.restapidesign.student.dto.CreateStudentRequest;
import com.gucardev.restapidesign.student.dto.PatchStudentRequest;
import com.gucardev.restapidesign.student.dto.StudentResponse;
import com.gucardev.restapidesign.student.dto.UpdateStudentRequest;
import jakarta.validation.Valid;
import jakarta.validation.Validator;
import jakarta.validation.ConstraintViolationException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Topic 1 — CRUD, partial updates with PATCH, and optimistic concurrency (ETag / If-Match). */
@RestController
@RequestMapping("/api/v1/students")
public class StudentController {

    private static final Map<String, Comparator<Student>> SORTS = Map.of(
            "fullName", Comparator.comparing(Student::fullName),
            "email", Comparator.comparing(Student::email),
            "createdAt", Comparator.comparing(Student::createdAt));

    private final StudentStore store;
    private final EnrollmentStore enrollments;
    private final CourseStore courses;
    private final Validator validator;

    public StudentController(StudentStore store, EnrollmentStore enrollments, CourseStore courses, Validator validator) {
        this.store = store;
        this.enrollments = enrollments;
        this.courses = courses;
        this.validator = validator;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public StudentResponse create(@Valid @RequestBody CreateStudentRequest request) {
        Student student = store.create(request.fullName(), request.email());
        return StudentResponse.from(student);
    }

    @GetMapping
    public PageResponse<StudentResponse> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {

        List<Student> all = store.findAll();
        if (sortBy != null) {
            Comparator<Student> comparator = SortSupport.resolve(sortBy, sortDir, SORTS);
            all = all.stream().sorted(comparator).toList();
        }
        List<StudentResponse> responses = all.stream().map(StudentResponse::from).toList();
        return PageSupport.paginate(responses, page, size);
    }

    @GetMapping("/{id}")
    public ResponseEntity<StudentResponse> getById(@PathVariable Long id) {
        Student student = store.findByIdOrThrow(id);
        return ResponseEntity.ok()
                .eTag(ETagSupport.format(student.version()))
                .body(StudentResponse.from(student));
    }

    @PutMapping("/{id}")
    public ResponseEntity<StudentResponse> replace(
            @PathVariable Long id,
            @RequestHeader(value = "If-Match", required = false) String ifMatch,
            @Valid @RequestBody UpdateStudentRequest request) {

        Student current = requireMatchingVersion(id, ifMatch);
        Student updated = new Student(current.id(), request.fullName(), request.email(),
                request.phoneNumber(), request.status(), current.version() + 1, current.createdAt());
        Student saved = store.replace(id, current.version(), updated);
        return ResponseEntity.ok()
                .eTag(ETagSupport.format(saved.version()))
                .body(StudentResponse.from(saved));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<StudentResponse> patch(
            @PathVariable Long id,
            @RequestHeader(value = "If-Match", required = false) String ifMatch,
            @RequestBody PatchStudentRequest request) {

        Student current = requireMatchingVersion(id, ifMatch);
        UpdateStudentRequest updated = new UpdateStudentRequest(
                request.fullName() != null ? request.fullName() : current.fullName(),
                request.email() != null ? request.email() : current.email(),
                request.phoneNumber() != null ? request.phoneNumber() : current.phoneNumber(),
                request.status() != null ? request.status() : current.status());
        var violations = validator.validate(updated);
        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
        }
        Student saved = store.replace(id, current.version(), new Student(current.id(), updated.fullName(),
                updated.email(), updated.phoneNumber(), updated.status(), current.version() + 1, current.createdAt()));
        return ResponseEntity.ok()
                .eTag(ETagSupport.format(saved.version()))
                .body(StudentResponse.from(saved));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        // Same monitor as enrollment creation: no new references can appear during deletion.
        synchronized (enrollments) {
            store.findByIdOrThrow(id);
            if (!enrollments.findByStudentId(id).isEmpty()) {
                throw new ConflictException("Student " + id + " has enrollment history and cannot be deleted");
            }
            store.deleteById(id);
        }
    }

    /** Read-only projection over EnrollmentStore — not separately stored data. */
    @GetMapping("/{id}/courses")
    public List<CourseResponse> enrolledCourses(@PathVariable Long id) {
        store.findByIdOrThrow(id);
        Set<Long> courseIds = enrollments.findByStudentId(id).stream()
                .filter(e -> e.status() != Enrollment.Status.DROPPED)
                .map(Enrollment::courseId)
                .collect(Collectors.toSet());
        return courseIds.stream()
                .map(courses::findByIdOrThrow)
                .map(CourseResponse::from)
                .toList();
    }

    private Student requireMatchingVersion(Long id, String ifMatch) {
        Student current = store.findByIdOrThrow(id);
        if (ifMatch == null) {
            throw new PreconditionRequiredException("If-Match header is required for this operation");
        }
        long expectedVersion = ETagSupport.parse(ifMatch);
        if (expectedVersion != current.version()) {
            throw new PreconditionFailedException("If-Match does not match the current resource version");
        }
        return current;
    }

}
