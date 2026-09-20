package com.gucardev.restapidesign.student;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

/** Proves v1 and v2 coexist against the same store with different response shapes. */
@SpringBootTest
@AutoConfigureMockMvc
class StudentV2ControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StudentStore store;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void clearStore() {
        store.clear();
    }

    @Test
    void v2SplitsFullNameWhileV1KeepsItWhole() throws Exception {
        String created = mockMvc.perform(post("/api/v1/students")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "fullName": "Jane Smith", "email": "jane@example.com" }
                                """))
                .andReturn().getResponse().getContentAsString();
        long id = objectMapper.readTree(created).get("id").asLong();

        mockMvc.perform(get("/api/v1/students/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Jane Smith"));

        mockMvc.perform(get("/api/v2/students/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Jane"))
                .andExpect(jsonPath("$.lastName").value("Smith"));
    }
}
