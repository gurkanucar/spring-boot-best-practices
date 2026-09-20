package com.gucardev.restapidesign.course;

import com.gucardev.restapidesign.common.ETagSupport;
import com.gucardev.restapidesign.common.PageResponse;
import com.gucardev.restapidesign.common.PageSupport;
import com.gucardev.restapidesign.common.SortSupport;
import com.gucardev.restapidesign.course.dto.CourseResponse;
import com.gucardev.restapidesign.course.dto.CreateCourseRequest;
import com.gucardev.restapidesign.course.dto.UpdateCourseRequest;
import com.gucardev.restapidesign.enrollment.Enrollment;
import com.gucardev.restapidesign.enrollment.EnrollmentStore;
import com.gucardev.restapidesign.error.ConflictException;
import com.gucardev.restapidesign.error.PreconditionFailedException;
import com.gucardev.restapidesign.error.PreconditionRequiredException;
import com.gucardev.restapidesign.student.Student;
import com.gucardev.restapidesign.student.StudentStore;
import com.gucardev.restapidesign.student.dto.StudentResponse;
import jakarta.validation.Valid;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

/** Topic 2 — CRUD/PATCH/ETag plus business actions (publish/archive) beyond CRUD. */
@RestController
@RequestMapping("/api/v1/courses")
public class CourseController {

    private static final Map<String, Comparator<Course>> SORTS = Map.of(
            "title", Comparator.comparing(Course::title),
            "capacity", Comparator.comparingInt(Course::capacity),
            "createdAt", Comparator.comparing(Course::createdAt));

    private final CourseStore store;
    private final EnrollmentStore enrollments;
    private final StudentStore students;

    public CourseController(CourseStore store, EnrollmentStore enrollments, StudentStore students) {
        this.store = store;
        this.enrollments = enrollments;
        this.students = students;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CourseResponse create(@Valid @RequestBody CreateCourseRequest request) {
        Course course = store.create(request.title(), request.description(), request.capacity());
        return CourseResponse.from(course);
    }

    @GetMapping
    public PageResponse<CourseResponse> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir,
            @RequestParam(required = false) Course.Status status) {

        List<Course> all = store.findAll();
        if (status != null) {
            all = all.stream().filter(c -> c.status() == status).toList();
        }
        if (sortBy != null) {
            Comparator<Course> comparator = SortSupport.resolve(sortBy, sortDir, SORTS);
            all = all.stream().sorted(comparator).toList();
        }
        List<CourseResponse> responses = all.stream().map(CourseResponse::from).toList();
        return PageSupport.paginate(responses, page, size);
    }

    @GetMapping("/{id}")
    public ResponseEntity<CourseResponse> getById(@PathVariable Long id) {
        Course course = store.findByIdOrThrow(id);
        return ResponseEntity.ok()
                .eTag(ETagSupport.format(course.version()))
                .body(CourseResponse.from(course));
    }

    @PutMapping("/{id}")
    public ResponseEntity<CourseResponse> replace(
            @PathVariable Long id,
            @RequestHeader(value = "If-Match", required = false) String ifMatch,
            @Valid @RequestBody UpdateCourseRequest request) {

        Course current = requireMatchingVersion(id, ifMatch);
        Course updated = new Course(current.id(), request.title(), request.description(),
                request.capacity(), current.status(), current.version() + 1, current.createdAt());
        Course saved = store.replace(id, updated);
        return ResponseEntity.ok()
                .eTag(ETagSupport.format(saved.version()))
                .body(CourseResponse.from(saved));
    }

    @PatchMapping(value = "/{id}", consumes = "application/merge-patch+json")
    public ResponseEntity<CourseResponse> patch(
            @PathVariable Long id,
            @RequestHeader(value = "If-Match", required = false) String ifMatch,
            @RequestBody JsonNode patch) {

        Course current = requireMatchingVersion(id, ifMatch);
        Course merged = applyPatch(current, patch);
        Course saved = store.replace(id, merged);
        return ResponseEntity.ok()
                .eTag(ETagSupport.format(saved.version()))
                .body(CourseResponse.from(saved));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        store.findByIdOrThrow(id);
        if (enrollments.countActiveByCourse(id) > 0) {
            throw new ConflictException("Course " + id + " has active enrollments and cannot be deleted");
        }
        store.deleteById(id);
    }

    @PostMapping("/{id}/publish")
    public CourseResponse publish(@PathVariable Long id) {
        Course current = store.findByIdOrThrow(id);
        if (current.status() != Course.Status.DRAFT) {
            throw new ConflictException("Course " + id + " cannot be published: status is " + current.status() + ", must be DRAFT");
        }
        Course updated = new Course(current.id(), current.title(), current.description(),
                current.capacity(), Course.Status.PUBLISHED, current.version() + 1, current.createdAt());
        return CourseResponse.from(store.replace(id, updated));
    }

    @PostMapping("/{id}/archive")
    public CourseResponse archive(@PathVariable Long id) {
        Course current = store.findByIdOrThrow(id);
        if (current.status() != Course.Status.PUBLISHED) {
            throw new ConflictException("Course " + id + " cannot be archived: status is " + current.status() + ", must be PUBLISHED");
        }
        Course updated = new Course(current.id(), current.title(), current.description(),
                current.capacity(), Course.Status.ARCHIVED, current.version() + 1, current.createdAt());
        return CourseResponse.from(store.replace(id, updated));
    }

    /** Read-only projection over EnrollmentStore — not separately stored data. */
    @GetMapping("/{id}/students")
    public List<StudentResponse> enrolledStudents(@PathVariable Long id) {
        store.findByIdOrThrow(id);
        Set<Long> studentIds = enrollments.findByCourseId(id).stream()
                .filter(e -> e.status() != Enrollment.Status.DROPPED)
                .map(Enrollment::studentId)
                .collect(java.util.stream.Collectors.toSet());
        return studentIds.stream()
                .map(students::findByIdOrThrow)
                .map(StudentResponse::from)
                .toList();
    }

    private Course requireMatchingVersion(Long id, String ifMatch) {
        Course current = store.findByIdOrThrow(id);
        if (ifMatch == null) {
            throw new PreconditionRequiredException("If-Match header is required for this operation");
        }
        long expectedVersion = ETagSupport.parse(ifMatch);
        if (expectedVersion != current.version()) {
            throw new PreconditionFailedException("If-Match does not match the current resource version");
        }
        return current;
    }

    /** RFC 7396 merge: absent field = unchanged, present = new value, explicit null = clear (description only). */
    private Course applyPatch(Course current, JsonNode patch) {
        String title = current.title();
        if (patch.has("title")) {
            if (patch.get("title").isNull()) {
                throw new IllegalArgumentException("title cannot be set to null");
            }
            title = patch.get("title").asText();
        }
        String description = current.description();
        if (patch.has("description")) {
            description = patch.get("description").isNull() ? null : patch.get("description").asText();
        }
        int capacity = current.capacity();
        if (patch.has("capacity")) {
            if (patch.get("capacity").isNull()) {
                throw new IllegalArgumentException("capacity cannot be set to null");
            }
            capacity = patch.get("capacity").asInt();
        }
        return new Course(current.id(), title, description, capacity, current.status(), current.version() + 1, current.createdAt());
    }
}
