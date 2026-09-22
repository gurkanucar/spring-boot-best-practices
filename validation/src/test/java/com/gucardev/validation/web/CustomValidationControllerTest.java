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

@WebMvcTest(CustomValidationController.class)
@Import({LocaleConfig.class, ProblemDetailFactory.class})
class CustomValidationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void acceptsValidCustomConstraints() throws Exception {
        mockMvc.perform(post("/api/custom/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "tcKimlikNo": "10000000146", "password": "Gucl!Parola1", "role": "admin" }
                                """))
                .andExpect(status().isOk());
    }

    @Test
    void rejectsAllThreeCustomConstraints() throws Exception {
        mockMvc.perform(post("/api/custom/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "tcKimlikNo": "11111111111", "password": "zayif", "role": "SUPERUSER" }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.length()").value(3))
                .andExpect(jsonPath("$.errors[?(@.field == 'tcKimlikNo')].code").value("TcKimlikNo"))
                .andExpect(jsonPath("$.errors[?(@.field == 'tcKimlikNo')].message")
                        .value("Gecersiz T.C. kimlik numarasi"))
                .andExpect(jsonPath("$.errors[?(@.field == 'role')].code").value("EnumValue"));
    }

    @Test
    void masksPasswordInRejectedValue() throws Exception {
        mockMvc.perform(post("/api/custom/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "tcKimlikNo": "10000000146", "password": "zayif", "role": "ADMIN" }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field == 'password')].rejectedValue").value("***"));
    }
}
