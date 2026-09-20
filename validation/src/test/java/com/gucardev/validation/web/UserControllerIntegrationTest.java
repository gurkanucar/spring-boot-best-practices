package com.gucardev.validation.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gucardev.validation.user.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * All layers active at once: DTO validation, service rules and database
 * constraints are exercised in the same flow.
 */
@SpringBootTest
@AutoConfigureMockMvc
class UserControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    private static String body(String email, String tcKimlikNo, int age) {
        return """
                {
                  "fullName": "Ayse Yilmaz",
                  "email": "%s",
                  "tcKimlikNo": "%s",
                  "age": %d,
                  "role": "VIEWER",
                  "address": { "city": "Ankara", "district": "Cankaya", "postalCode": "06500" }
                }
                """.formatted(email, tcKimlikNo, age);
    }

    @BeforeEach
    void clearDatabase() {
        userRepository.deleteAll();
    }

    /** Rows committed with DB_CLOSE_DELAY=-1 would otherwise outlive this test class. */
    @AfterEach
    void clearDatabaseAfter() {
        userRepository.deleteAll();
    }

    @Test
    void createsUser() throws Exception {
        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("ayse@example.com", "10000000146", 30)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.email").value("ayse@example.com"));
    }

    @Test
    void rejectsDuplicateEmailAtApplicationLayerWithFieldError() throws Exception {
        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("ayse@example.com", "10000000146", 30)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("ayse@example.com", "12345678950", 30)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("email"))
                .andExpect(jsonPath("$.errors[0].code").value("UniqueEmail"))
                .andExpect(jsonPath("$.errors[0].message").value("Bu e-posta adresi zaten kayitli"));
    }

    @Test
    void fallsBackToDatabaseConstraintWhenApplicationCheckIsSkipped() throws Exception {
        mockMvc.perform(post("/api/users/unsafe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("ayse@example.com", "10000000146", 30)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/users/unsafe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("ayse@example.com", "12345678950", 30)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.detail").value("Bu e-posta adresi zaten kayitli"));
    }

    @Test
    void fallsBackToDatabaseConstraintForDuplicateTcKimlikNo() throws Exception {
        mockMvc.perform(post("/api/users/unsafe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("ilk@example.com", "10000000146", 30)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/users/unsafe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("ikinci@example.com", "10000000146", 30)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Bu T.C. kimlik numarasi zaten kayitli"));
    }

    @Test
    void fallsBackToDatabaseConstraintForUnderageUser() throws Exception {
        mockMvc.perform(post("/api/users/unsafe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("cocuk@example.com", "10000000146", 15)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Yas en az 18 olmalidir"));
    }

    @Test
    void rejectsUnderageRequestAtDtoLayer() throws Exception {
        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("cocuk@example.com", "10000000146", 15)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("age"))
                .andExpect(jsonPath("$.errors[0].code").value("Min"));
    }

    @Test
    void returnsNotFoundForMissingUser() throws Exception {
        mockMvc.perform(get("/api/users/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Bulunamadi"));
    }

    @Test
    void rejectsIdBelowMinimumFromServiceLayer() throws Exception {
        mockMvc.perform(get("/api/users/0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("id"))
                .andExpect(jsonPath("$.errors[0].code").value("Min"));
    }
}
