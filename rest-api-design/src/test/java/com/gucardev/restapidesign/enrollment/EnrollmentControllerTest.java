package com.gucardev.restapidesign.enrollment;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gucardev.restapidesign.course.CourseStore;
import com.gucardev.restapidesign.student.StudentStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

/** Enrollment as a first-class resource: create (enroll) with business rules, then complete/drop actions only. */
@SpringBootTest
@AutoConfigureMockMvc
class EnrollmentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StudentStore studentStore;

    @Autowired
    private CourseStore courseStore;

    @Autowired
    private EnrollmentStore enrollmentStore;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void clearStores() {
        studentStore.clear();
        courseStore.clear();
        enrollmentStore.clear();
    }

    private long createStudent(String email) throws Exception {
        String response = mockMvc.perform(post("/api/v1/students")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"fullName\": \"Student\", \"email\": \"%s\" }".formatted(email)))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }

    private long createPublishedCourse(String title, int capacity) throws Exception {
        String response = mockMvc.perform(post("/api/v1/courses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"title\": \"%s\", \"description\": \"d\", \"capacity\": %d }".formatted(title, capacity)))
                .andReturn().getResponse().getContentAsString();
        long id = objectMapper.readTree(response).get("id").asLong();
        mockMvc.perform(post("/api/v1/courses/" + id + "/publish")).andExpect(status().isOk());
        return id;
    }

    private long enroll(long studentId, long courseId) throws Exception {
        String response = mockMvc.perform(post("/api/v1/enrollments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"studentId\": %d, \"courseId\": %d }".formatted(studentId, courseId)))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }

    @Test
    void createEnrollsStudentInPublishedCourse() throws Exception {
        long studentId = createStudent("jane@example.com");
        long courseId = createPublishedCourse("Intro", 10);

        mockMvc.perform(post("/api/v1/enrollments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"studentId\": %d, \"courseId\": %d }".formatted(studentId, courseId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void createFailsWhenCourseIsNotPublished() throws Exception {
        long studentId = createStudent("jane@example.com");
        String response = mockMvc.perform(post("/api/v1/courses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "title": "Draft Course", "description": "d", "capacity": 10 }
                                """))
                .andReturn().getResponse().getContentAsString();
        long courseId = objectMapper.readTree(response).get("id").asLong();

        mockMvc.perform(post("/api/v1/enrollments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"studentId\": %d, \"courseId\": %d }".formatted(studentId, courseId)))
                .andExpect(status().isConflict());
    }

    @Test
    void createFailsOnDuplicateActiveEnrollment() throws Exception {
        long studentId = createStudent("jane@example.com");
        long courseId = createPublishedCourse("Intro", 10);
        enroll(studentId, courseId);

        mockMvc.perform(post("/api/v1/enrollments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"studentId\": %d, \"courseId\": %d }".formatted(studentId, courseId)))
                .andExpect(status().isConflict());
    }

    @Test
    void createFailsWhenCourseIsAtCapacity() throws Exception {
        long courseId = createPublishedCourse("Small", 1);
        long firstStudent = createStudent("first@example.com");
        long secondStudent = createStudent("second@example.com");
        enroll(firstStudent, courseId);

        mockMvc.perform(post("/api/v1/enrollments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"studentId\": %d, \"courseId\": %d }".formatted(secondStudent, courseId)))
                .andExpect(status().isConflict());
    }

    @Test
    void completeTransitionsActiveEnrollmentAndBlocksOnNonActive() throws Exception {
        long studentId = createStudent("jane@example.com");
        long courseId = createPublishedCourse("Intro", 10);
        long enrollmentId = enroll(studentId, courseId);

        mockMvc.perform(post("/api/v1/enrollments/" + enrollmentId + "/complete"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        mockMvc.perform(post("/api/v1/enrollments/" + enrollmentId + "/complete"))
                .andExpect(status().isConflict());
    }

    @Test
    void dropTransitionsActiveEnrollmentAndFreesCapacity() throws Exception {
        long courseId = createPublishedCourse("Small", 1);
        long firstStudent = createStudent("first@example.com");
        long secondStudent = createStudent("second@example.com");
        long firstEnrollment = enroll(firstStudent, courseId);

        mockMvc.perform(post("/api/v1/enrollments/" + firstEnrollment + "/drop"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DROPPED"));

        // Capacity freed up by the drop — the second student can now enroll.
        mockMvc.perform(post("/api/v1/enrollments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"studentId\": %d, \"courseId\": %d }".formatted(secondStudent, courseId)))
                .andExpect(status().isCreated());
    }

    @Test
    void listFiltersByStudentCourseAndStatus() throws Exception {
        long studentId = createStudent("jane@example.com");
        long courseId = createPublishedCourse("Intro", 10);
        enroll(studentId, courseId);

        mockMvc.perform(get("/api/v1/enrollments").param("studentId", String.valueOf(studentId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));

        mockMvc.perform(get("/api/v1/enrollments").param("status", "COMPLETED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }
}
