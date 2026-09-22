package com.gucardev.restapidesign.course;

import com.gucardev.restapidesign.error.ResourceNotFoundException;
import com.gucardev.restapidesign.error.PreconditionFailedException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;

/** In-memory Course storage; thread-safe via ConcurrentHashMap and copy-on-write records. */
@Component
public class CourseStore {

    private final Map<Long, Course> courses = new ConcurrentHashMap<>();
    private final AtomicLong idSequence = new AtomicLong();

    public Course create(String title, String description, int capacity) {
        long id = idSequence.incrementAndGet();
        Course course = new Course(id, title, description, capacity, Course.Status.DRAFT, 0L, Instant.now());
        courses.put(id, course);
        return course;
    }

    public Course findByIdOrThrow(Long id) {
        Course course = courses.get(id);
        if (course == null) {
            throw new ResourceNotFoundException("Course " + id + " not found");
        }
        return course;
    }

    public boolean existsById(Long id) {
        return courses.containsKey(id);
    }

    public List<Course> findAll() {
        return List.copyOf(courses.values());
    }

    /** Version comparison and replacement are one atomic operation. */
    public synchronized Course replace(Long id, long expectedVersion, Course updated) {
        Course current = findByIdOrThrow(id);
        if (current.version() != expectedVersion) {
            throw new PreconditionFailedException("Resource changed concurrently; fetch it again before retrying");
        }
        courses.put(id, updated);
        return updated;
    }

    public synchronized void deleteById(Long id) {
        findByIdOrThrow(id);
        courses.remove(id);
    }

    /** Test-only: wipe all state so tests are order-independent. */
    public synchronized void clear() {
        courses.clear();
        idSequence.set(0);
    }
}
