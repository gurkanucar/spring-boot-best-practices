package com.gucardev.restapidesign.enrollment;

import com.gucardev.restapidesign.error.ResourceNotFoundException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;

/** In-memory Enrollment storage; thread-safe via ConcurrentHashMap and copy-on-write records. */
@Component
public class EnrollmentStore {

    private final Map<Long, Enrollment> enrollments = new ConcurrentHashMap<>();
    private final AtomicLong idSequence = new AtomicLong();

    public Enrollment create(Long studentId, Long courseId) {
        long id = idSequence.incrementAndGet();
        Enrollment enrollment = new Enrollment(id, studentId, courseId, Enrollment.Status.ACTIVE, Instant.now(), null);
        enrollments.put(id, enrollment);
        return enrollment;
    }

    public Enrollment findByIdOrThrow(Long id) {
        Enrollment enrollment = enrollments.get(id);
        if (enrollment == null) {
            throw new ResourceNotFoundException("Enrollment " + id + " not found");
        }
        return enrollment;
    }

    public List<Enrollment> findAll() {
        return List.copyOf(enrollments.values());
    }

    public List<Enrollment> findByStudentId(Long studentId) {
        return enrollments.values().stream().filter(e -> e.studentId().equals(studentId)).toList();
    }

    public List<Enrollment> findByCourseId(Long courseId) {
        return enrollments.values().stream().filter(e -> e.courseId().equals(courseId)).toList();
    }

    public boolean hasActiveEnrollment(Long studentId, Long courseId) {
        return enrollments.values().stream()
                .anyMatch(e -> e.studentId().equals(studentId) && e.courseId().equals(courseId)
                        && e.status() == Enrollment.Status.ACTIVE);
    }

    public long countActiveByCourse(Long courseId) {
        return enrollments.values().stream()
                .filter(e -> e.courseId().equals(courseId) && e.status() == Enrollment.Status.ACTIVE)
                .count();
    }

    public Enrollment replace(Long id, Enrollment updated) {
        enrollments.put(id, updated);
        return updated;
    }

    /** Test-only: wipe all state so tests are order-independent. */
    public void clear() {
        enrollments.clear();
        idSequence.set(0);
    }
}
