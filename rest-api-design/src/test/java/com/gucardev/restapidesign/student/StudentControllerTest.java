package com.gucardev.restapidesign.student;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;
import com.gucardev.restapidesign.enrollment.EnrollmentStore;

/** CRUD, pagination/sort, partial updates with PATCH, and ETag/If-Match concurrency. */
@SpringBootTest
@AutoConfigureMockMvc
class StudentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StudentStore store;

    @Autowired
    private EnrollmentStore enrollments;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void clearStore() {
        enrollments.clear();
        store.clear();
    }

    private long createStudent(String fullName, String email) throws Exception {
        String response = mockMvc.perform(post("/api/v1/students")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"fullName\": \"%s\", \"email\": \"%s\" }".formatted(fullName, email)))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }

    @Test
    void createsStudent() throws Exception {
        mockMvc.perform(post("/api/v1/students")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "fullName": "Jane Smith", "email": "jane@example.com" }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void rejectsInvalidCreateRequestWithFieldErrors() throws Exception {
        mockMvc.perform(post("/api/v1/students")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "fullName": "", "email": "not-an-email" }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation Failed"))
                .andExpect(jsonPath("$.errors.length()").value(2));
    }

    @Test
    void rejectsDuplicateEmailWithConflict() throws Exception {
        createStudent("Jane Smith", "jane@example.com");

        mockMvc.perform(post("/api/v1/students")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "fullName": "Another Jane", "email": "jane@example.com" }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Conflict"));
    }

    @Test
    void getByIdReturnsStudentWithETag() throws Exception {
        long id = createStudent("Jane Smith", "jane@example.com");

        mockMvc.perform(get("/api/v1/students/" + id))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"0\""));
    }

    @Test
    void getByIdReturnsNotFoundForMissingStudent() throws Exception {
        mockMvc.perform(get("/api/v1/students/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Not Found"));
    }

    @Test
    void listSupportsPaginationAndSort() throws Exception {
        createStudent("Bob", "bob@example.com");
        createStudent("Alice", "alice@example.com");

        mockMvc.perform(get("/api/v1/students").param("sortBy", "fullName").param("sortDir", "asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].fullName").value("Alice"))
                .andExpect(jsonPath("$.content[1].fullName").value("Bob"))
                .andExpect(jsonPath("$.totalElements").value(2));

        mockMvc.perform(get("/api/v1/students").param("sortBy", "nope"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deletesStudent() throws Exception {
        long id = createStudent("Jane Smith", "jane@example.com");

        mockMvc.perform(delete("/api/v1/students/" + id)).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/students/" + id)).andExpect(status().isNotFound());
    }

    @Test
    void putRequiresIfMatchAndRejectsStaleVersion() throws Exception {
        long id = createStudent("Jane Smith", "jane@example.com");
        String body = """
                { "fullName": "Jane Doe", "email": "jane@example.com", "phoneNumber": null, "status": "ACTIVE" }
                """;

        mockMvc.perform(put("/api/v1/students/" + id).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isPreconditionRequired());

        mockMvc.perform(put("/api/v1/students/" + id).header("If-Match", "\"99\"")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isPreconditionFailed());

        mockMvc.perform(put("/api/v1/students/" + id).header("If-Match", "\"0\"")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"1\""))
                .andExpect(jsonPath("$.fullName").value("Jane Doe"));
    }

    @Test
    void patchUpdatesOnlySuppliedFieldsAndIgnoresNull() throws Exception {
        long id = createStudent("Jane Smith", "jane@example.com");

        mockMvc.perform(patch("/api/v1/students/" + id).header("If-Match", "\"0\"")
                        .contentType("application/json")
                        .content("""
                                { "phoneNumber": "+15550001111" }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Jane Smith"))
                .andExpect(jsonPath("$.phoneNumber").value("+15550001111"));

        mockMvc.perform(patch("/api/v1/students/" + id).header("If-Match", "\"1\"")
                        .contentType("application/json")
                        .content("""
                                { "phoneNumber": null }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phoneNumber").value("+15550001111"))
                .andExpect(jsonPath("$.fullName").value("Jane Smith"));
    }

    @Test
    void patchRejectsBlankFullName() throws Exception {
        long id = createStudent("Jane Smith", "jane@example.com");

        mockMvc.perform(patch("/api/v1/students/" + id).header("If-Match", "\"0\"")
                        .contentType("application/json")
                        .content("""
                                { "fullName": " " }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void patchAcceptsOrdinaryJsonAndPreservesOtherFields() throws Exception {
        long id = createStudent("Jane Smith", "jane@example.com");

        mockMvc.perform(patch("/api/v1/students/" + id).header("If-Match", "\"0\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "email": "new@example.com" }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("new@example.com"))
                .andExpect(jsonPath("$.fullName").value("Jane Smith"));
    }
}
