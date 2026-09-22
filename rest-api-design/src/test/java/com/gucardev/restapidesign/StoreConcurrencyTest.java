package com.gucardev.restapidesign;

import static org.assertj.core.api.Assertions.*;

import com.gucardev.restapidesign.course.Course;
import com.gucardev.restapidesign.course.CourseStore;
import com.gucardev.restapidesign.student.Student;
import com.gucardev.restapidesign.student.StudentStore;
import com.gucardev.restapidesign.error.PreconditionFailedException;
import com.gucardev.restapidesign.error.ResourceNotFoundException;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class StoreConcurrencyTest {

    @Test
    void onlyOneStudentUpdateCanUseTheSameVersion() throws Exception {
        var store = new StudentStore();
        var original = store.create("Original", "student@example.com");
        var first = new Student(original.id(), "First", original.email(), null, original.status(), 1, original.createdAt());
        var second = new Student(original.id(), "Second", original.email(), null, original.status(), 1, original.createdAt());
        assertOneWinner(() -> store.replace(original.id(), 0, first), () -> store.replace(original.id(), 0, second));
        assertThat(store.findByIdOrThrow(original.id()).version()).isEqualTo(1);
    }

    @Test
    void onlyOneCourseUpdateCanUseTheSameVersion() throws Exception {
        var store = new CourseStore();
        var original = store.create("Original", null, 10);
        var first = new Course(original.id(), "First", null, 10, original.status(), 1, original.createdAt());
        var second = new Course(original.id(), "Second", null, 10, original.status(), 1, original.createdAt());
        assertOneWinner(() -> store.replace(original.id(), 0, first), () -> store.replace(original.id(), 0, second));
        assertThat(store.findByIdOrThrow(original.id()).version()).isEqualTo(1);
    }

    @Test
    void staleUpdatesCannotResurrectDeletedResources() {
        var students = new StudentStore();
        var student = students.create("Original", "student@example.com");
        students.deleteById(student.id());
        assertThatThrownBy(() -> students.replace(student.id(), 0, student)).isInstanceOf(ResourceNotFoundException.class);
        var courses = new CourseStore();
        var course = courses.create("Original", null, 10);
        courses.deleteById(course.id());
        assertThatThrownBy(() -> courses.replace(course.id(), 0, course)).isInstanceOf(ResourceNotFoundException.class);
    }

    private void assertOneWinner(Callable<?> first, Callable<?> second) throws Exception {
        var barrier = new CyclicBarrier(2);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var a = executor.submit(() -> attempt(barrier, first));
            var b = executor.submit(() -> attempt(barrier, second));
            assertThat(List.of(a.get(10, TimeUnit.SECONDS), b.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder("updated", "stale");
        }
    }

    private String attempt(CyclicBarrier barrier, Callable<?> update) throws Exception {
        barrier.await(5, TimeUnit.SECONDS);
        try {
            update.call();
            return "updated";
        } catch (PreconditionFailedException e) {
            return "stale";
        }
    }
}
