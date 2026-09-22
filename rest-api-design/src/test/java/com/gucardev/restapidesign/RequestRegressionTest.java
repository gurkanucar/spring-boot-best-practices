package com.gucardev.restapidesign;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.gucardev.restapidesign.course.CourseStore;
import com.gucardev.restapidesign.enrollment.Enrollment;
import com.gucardev.restapidesign.enrollment.EnrollmentStore;
import com.gucardev.restapidesign.student.StudentStore;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class RequestRegressionTest {

    @Autowired MockMvc mvc;
    @Autowired StudentStore students;
    @Autowired CourseStore courses;
    @Autowired EnrollmentStore enrollments;

    @BeforeEach
    void clearStores() {
        enrollments.clear();
        students.clear();
        courses.clear();
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"fullName\":\"\"}", "{\"email\":\"bad\"}",
            "{\"phoneNumber\":{}}", "{\"status\":\"UNKNOWN\"}", "[]", "42"})
    void invalidStudentPatchDoesNotChangeTheResource(String body) throws Exception {
        var student = students.create("Original", "original@example.com");
        mvc.perform(patch("/api/v1/students/" + student.id()).header("If-Match", "\"0\"")
                        .contentType("application/json").content(body))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/students/" + student.id()))
                .andExpect(header().string("ETag", "\"0\""))
                .andExpect(jsonPath("$.fullName").value("Original"))
                .andExpect(jsonPath("$.email").value("original@example.com"));
    }

    @Test
    void patchValidationReturnsFieldErrors() throws Exception {
        var student = students.create("Original", "original@example.com");
        mvc.perform(patch("/api/v1/students/" + student.id()).header("If-Match", "\"0\"")
                        .contentType("application/json").content("{\"email\":\"bad\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("email"))
                .andExpect(jsonPath("$.errors[0].code").value("Email"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"capacity\":-5}", "{\"capacity\":0}",
            "{\"capacity\":\"invalid\"}", "{\"capacity\":2147483648}",
            "{\"title\":\" \"}", "{\"description\":{}}", "[]"})
    void invalidCoursePatchDoesNotChangeTheResource(String body) throws Exception {
        var course = courses.create("Original", "Description", 10);
        mvc.perform(patch("/api/v1/courses/" + course.id()).header("If-Match", "\"0\"")
                        .contentType("application/json").content(body))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/courses/" + course.id()))
                .andExpect(header().string("ETag", "\"0\""))
                .andExpect(jsonPath("$.capacity").value(10));
    }

    @ParameterizedTest
    @ValueSource(strings = {"students", "courses", "enrollments"})
    void paginationRejectsInvalidBoundsAndHandlesLargeOffsets(String resource) throws Exception {
        String path = "/api/v1/" + resource;
        mvc.perform(get(path).param("page", "-1")).andExpect(status().isBadRequest());
        for (String size : new String[]{"-1", "0", "101"}) {
            mvc.perform(get(path).param("size", size)).andExpect(status().isBadRequest());
        }
        mvc.perform(get(path).param("page", "2147483647").param("size", "100"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content").isEmpty());
    }

    @ParameterizedTest
    @EnumSource(Enrollment.Status.class)
    void enrollmentHistoryPreventsParentDeletion(Enrollment.Status status) throws Exception {
        var student = students.create("Student", "student@example.com");
        var course = courses.create("Course", null, 10);
        var enrollment = enrollments.create(student.id(), course.id());
        enrollments.replace(enrollment.id(), new Enrollment(enrollment.id(), student.id(), course.id(),
                status, enrollment.enrolledAt(), Instant.now()));
        mvc.perform(delete("/api/v1/students/" + student.id())).andExpect(status().isConflict());
        mvc.perform(delete("/api/v1/courses/" + course.id())).andExpect(status().isConflict());
        mvc.perform(get("/api/v1/courses/" + course.id() + "/students")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/students/" + student.id() + "/courses")).andExpect(status().isOk());
    }
}
