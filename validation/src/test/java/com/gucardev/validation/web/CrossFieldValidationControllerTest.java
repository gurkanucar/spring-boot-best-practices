package com.gucardev.validation.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

@WebMvcTest(CrossFieldValidationController.class)
@Import({LocaleConfig.class, ProblemDetailFactory.class})
class CrossFieldValidationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void rejectsMismatchedPasswordsWithFieldLevelError() throws Exception {
        mockMvc.perform(post("/api/cross-field/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "password": "Gucl!Parola1",
                                  "passwordConfirm": "BaskaParola1!",
                                  "startDate": "2026-01-01",
                                  "endDate": "2026-12-31"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.length()").value(1))
                .andExpect(jsonPath("$.errors[0].field").value("passwordConfirm"))
                .andExpect(jsonPath("$.errors[0].code").value("PasswordsMatch"))
                .andExpect(jsonPath("$.errors[0].message").value("Sifreler eslesmiyor"))
                .andExpect(jsonPath("$.errors[0].rejectedValue").value("***"));
    }

    @Test
    void rejectsInvertedDateRange() throws Exception {
        mockMvc.perform(post("/api/cross-field/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "password": "Gucl!Parola1",
                                  "passwordConfirm": "Gucl!Parola1",
                                  "startDate": "2026-12-31",
                                  "endDate": "2026-01-01"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("endDate"))
                .andExpect(jsonPath("$.errors[0].code").value("ValidDateRange"));
    }
}
