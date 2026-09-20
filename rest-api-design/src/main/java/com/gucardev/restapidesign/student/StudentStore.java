package com.gucardev.restapidesign.student;

import com.gucardev.restapidesign.error.ConflictException;
import com.gucardev.restapidesign.error.ResourceNotFoundException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;

/** In-memory Student storage; thread-safe via ConcurrentHashMap and copy-on-write records. */
@Component
public class StudentStore {

    private final Map<Long, Student> students = new ConcurrentHashMap<>();
    private final AtomicLong idSequence = new AtomicLong();

    public Student create(String fullName, String email) {
        assertEmailAvailable(email, null);
        long id = idSequence.incrementAndGet();
        Student student = new Student(id, fullName, email, null, Student.Status.ACTIVE, 0L, Instant.now());
        students.put(id, student);
        return student;
    }

    public Student findByIdOrThrow(Long id) {
        Student student = students.get(id);
        if (student == null) {
            throw new ResourceNotFoundException("Student " + id + " not found");
        }
        return student;
    }

    public boolean existsById(Long id) {
        return students.containsKey(id);
    }

    public List<Student> findAll() {
        return List.copyOf(students.values());
    }

    public Student replace(Long id, Student updated) {
        students.put(id, updated);
        return updated;
    }

    public void deleteById(Long id) {
        findByIdOrThrow(id);
        students.remove(id);
    }

    /** Case-insensitive; excludingId lets an update check uniqueness against every OTHER student. */
    public void assertEmailAvailable(String email, Long excludingId) {
        boolean taken = students.values().stream()
                .anyMatch(s -> s.email().equalsIgnoreCase(email) && !s.id().equals(excludingId));
        if (taken) {
            throw new ConflictException("Email already registered: " + email);
        }
    }

    /** Test-only: wipe all state so tests are order-independent. */
    public void clear() {
        students.clear();
        idSequence.set(0);
    }
}
