package com.gucardev.restapidesign.course;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gucardev.restapidesign.enrollment.EnrollmentStore;
import com.gucardev.restapidesign.student.StudentStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

/** CRUD/PATCH/ETag plus the publish/archive business actions and the enrollment delete-guard. */
@SpringBootTest
@AutoConfigureMockMvc
class CourseControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CourseStore courseStore;

    @Autowired
    private StudentStore studentStore;

    @Autowired
    private EnrollmentStore enrollmentStore;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void clearStores() {
        courseStore.clear();
        studentStore.clear();
        enrollmentStore.clear();
    }

    private long createCourse(String title, int capacity) throws Exception {
        String response = mockMvc.perform(post("/api/v1/courses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"title\": \"%s\", \"description\": \"d\", \"capacity\": %d }".formatted(title, capacity)))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }

    private long createStudent(String email) throws Exception {
        String response = mockMvc.perform(post("/api/v1/students")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"fullName\": \"Student\", \"email\": \"%s\" }".formatted(email)))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }

    @Test
    void createsCourseInDraftStatus() throws Exception {
        mockMvc.perform(post("/api/v1/courses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "title": "Intro to REST", "description": "Basics", "capacity": 10 }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"));
    }

    @Test
    void publishOnlySucceedsFromDraft() throws Exception {
        long id = createCourse("Intro", 10);

        mockMvc.perform(post("/api/v1/courses/" + id + "/publish"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"));

        mockMvc.perform(post("/api/v1/courses/" + id + "/publish"))
                .andExpect(status().isConflict());
    }

    @Test
    void archiveOnlySucceedsFromPublished() throws Exception {
        long id = createCourse("Intro", 10);

        mockMvc.perform(post("/api/v1/courses/" + id + "/archive"))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/api/v1/courses/" + id + "/publish")).andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/courses/" + id + "/archive"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ARCHIVED"));
    }

    @Test
    void patchClearsDescriptionWithExplicitNullAndRejectsNullTitle() throws Exception {
        long id = createCourse("Intro", 10);

        mockMvc.perform(patch("/api/v1/courses/" + id).header("If-Match", "\"0\"")
                        .contentType("application/merge-patch+json")
                        .content("""
                                { "description": null }
                                """))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"1\""))
                .andExpect(jsonPath("$.description").doesNotExist());

        mockMvc.perform(patch("/api/v1/courses/" + id).header("If-Match", "\"1\"")
                        .contentType("application/merge-patch+json")
                        .content("""
                                { "title": null }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deleteIsBlockedByAnActiveEnrollment() throws Exception {
        long courseId = createCourse("Intro", 10);
        long studentId = createStudent("student@example.com");
        mockMvc.perform(post("/api/v1/courses/" + courseId + "/publish")).andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/enrollments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"studentId\": %d, \"courseId\": %d }".formatted(studentId, courseId)))
                .andExpect(status().isCreated());

        mockMvc.perform(delete("/api/v1/courses/" + courseId))
                .andExpect(status().isConflict());
    }

    @Test
    void relationshipViewReturnsEnrolledStudents() throws Exception {
        long courseId = createCourse("Intro", 10);
        long studentId = createStudent("student@example.com");
        mockMvc.perform(post("/api/v1/courses/" + courseId + "/publish")).andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/enrollments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"studentId\": %d, \"courseId\": %d }".formatted(studentId, courseId)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/courses/" + courseId + "/students"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(studentId));

        mockMvc.perform(get("/api/v1/students/" + studentId + "/courses"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(courseId));
    }

    @Test
    void listFiltersByStatusAndSorts() throws Exception {
        createCourse("Zeta", 10);
        long alphaId = createCourse("Alpha", 10);
        mockMvc.perform(post("/api/v1/courses/" + alphaId + "/publish")).andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/courses").param("status", "PUBLISHED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].title").value("Alpha"));

        mockMvc.perform(get("/api/v1/courses").param("sortBy", "title").param("sortDir", "asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].title").value("Alpha"))
                .andExpect(jsonPath("$.content[1].title").value("Zeta"));
    }
}
