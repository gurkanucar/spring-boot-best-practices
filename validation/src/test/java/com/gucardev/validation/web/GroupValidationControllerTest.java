package com.gucardev.validation.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gucardev.validation.config.LocaleConfig;
import com.gucardev.validation.error.ProblemDetailFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(GroupValidationController.class)
@Import({LocaleConfig.class, ProblemDetailFactory.class})
class GroupValidationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void createRejectsSuppliedIdAndMissingPassword() throws Exception {
        mockMvc.perform(post("/api/groups/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "id": 5, "fullName": "Ayse Yilmaz", "email": "ayse@example.com" }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.length()").value(2))
                .andExpect(jsonPath("$.errors[?(@.field == 'id')].code").value("Null"))
                .andExpect(jsonPath("$.errors[?(@.field == 'password')].code").value("NotBlank"));
    }

    @Test
    void createAcceptsRequestWithoutId() throws Exception {
        mockMvc.perform(post("/api/groups/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "fullName": "Ayse Yilmaz", "email": "ayse@example.com", "password": "Gucl!Parola1" }
                                """))
                .andExpect(status().isOk());
    }

    @Test
    void updateRequiresIdAndAllowsMissingPassword() throws Exception {
        mockMvc.perform(put("/api/groups/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "fullName": "Ayse Yilmaz", "email": "ayse@example.com" }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.length()").value(1))
                .andExpect(jsonPath("$.errors[0].field").value("id"))
                .andExpect(jsonPath("$.errors[0].code").value("NotNull"));
    }

    @Test
    void updateAcceptsRequestWithIdAndNoPassword() throws Exception {
        mockMvc.perform(put("/api/groups/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "id": 5, "fullName": "Ayse Yilmaz", "email": "ayse@example.com" }
                                """))
                .andExpect(status().isOk());
    }
}
