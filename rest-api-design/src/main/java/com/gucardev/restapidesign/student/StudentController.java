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
import com.gucardev.restapidesign.error.PreconditionRequiredException;
import com.gucardev.restapidesign.student.dto.CreateStudentRequest;
import com.gucardev.restapidesign.student.dto.StudentResponse;
import com.gucardev.restapidesign.student.dto.UpdateStudentRequest;
import jakarta.validation.Valid;
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
import tools.jackson.databind.JsonNode;

/** Topic 1 — CRUD, PATCH (JSON Merge Patch), and optimistic concurrency (ETag / If-Match). */
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

    public StudentController(StudentStore store, EnrollmentStore enrollments, CourseStore courses) {
        this.store = store;
        this.enrollments = enrollments;
        this.courses = courses;
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
        store.assertEmailAvailable(request.email(), current.id());
        Student updated = new Student(current.id(), request.fullName(), request.email(),
                request.phoneNumber(), request.status(), current.version() + 1, current.createdAt());
        Student saved = store.replace(id, updated);
        return ResponseEntity.ok()
                .eTag(ETagSupport.format(saved.version()))
                .body(StudentResponse.from(saved));
    }

    @PatchMapping(value = "/{id}", consumes = "application/merge-patch+json")
    public ResponseEntity<StudentResponse> patch(
            @PathVariable Long id,
            @RequestHeader(value = "If-Match", required = false) String ifMatch,
            @RequestBody JsonNode patch) {

        Student current = requireMatchingVersion(id, ifMatch);
        Student merged = applyPatch(current, patch);
        Student saved = store.replace(id, merged);
        return ResponseEntity.ok()
                .eTag(ETagSupport.format(saved.version()))
                .body(StudentResponse.from(saved));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        store.deleteById(id);
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

    /** RFC 7396 merge: absent field = unchanged, present = new value, explicit null = clear (phoneNumber only). */
    private Student applyPatch(Student current, JsonNode patch) {
        String fullName = current.fullName();
        if (patch.has("fullName")) {
            if (patch.get("fullName").isNull()) {
                throw new IllegalArgumentException("fullName cannot be set to null");
            }
            fullName = patch.get("fullName").asText();
        }
        String email = current.email();
        if (patch.has("email")) {
            if (patch.get("email").isNull()) {
                throw new IllegalArgumentException("email cannot be set to null");
            }
            email = patch.get("email").asText();
            store.assertEmailAvailable(email, current.id());
        }
        String phoneNumber = current.phoneNumber();
        if (patch.has("phoneNumber")) {
            phoneNumber = patch.get("phoneNumber").isNull() ? null : patch.get("phoneNumber").asText();
        }
        return new Student(current.id(), fullName, email, phoneNumber, current.status(), current.version() + 1, current.createdAt());
    }
}
