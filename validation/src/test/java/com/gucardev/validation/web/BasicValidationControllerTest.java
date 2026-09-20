package com.gucardev.validation.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gucardev.validation.config.LocaleConfig;
import com.gucardev.validation.config.ValidationConfig;
import com.gucardev.validation.error.ProblemDetailFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(BasicValidationController.class)
@Import({ValidationConfig.class, LocaleConfig.class, ProblemDetailFactory.class})
class BasicValidationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private static final String VALID_BODY = """
            {
              "fullName": "Ayse Yilmaz",
              "email": "ayse@example.com",
              "age": 30,
              "birthDate": "1996-05-04",
              "phone": "05321234567"
            }
            """;

    private static final String INVALID_BODY = """
            {
              "fullName": "",
              "email": "gecersiz-eposta",
              "age": 12,
              "birthDate": "2090-01-01",
              "phone": "123"
            }
            """;

    @Test
    void acceptsValidRequest() throws Exception {
        mockMvc.perform(post("/api/basic/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("ayse@example.com"));
    }

    @Test
    void rejectsInvalidRequestWithProblemDetail() throws Exception {
        mockMvc.perform(post("/api/basic/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(INVALID_BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("Dogrulama Hatasi"))
                .andExpect(jsonPath("$.instance").value("/api/basic/users"))
                // fullName "" fails both @NotBlank and @Size(min=3): 5 fields, 6 errors.
                .andExpect(jsonPath("$.errors.length()").value(6))
                .andExpect(jsonPath("$.errors[?(@.field == 'email')].message")
                        .value("Gecerli bir e-posta adresi giriniz"))
                .andExpect(jsonPath("$.errors[?(@.field == 'age')].code").value("Min"));
    }

    @Test
    void returnsEnglishMessagesWhenAcceptLanguageIsEnglish() throws Exception {
        mockMvc.perform(post("/api/basic/users")
                        .header("Accept-Language", "en")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(INVALID_BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation Failed"))
                .andExpect(jsonPath("$.errors[?(@.field == 'email')].message")
                        .value("Please provide a valid email address"));
    }

    @Test
    void rejectsMalformedJson() throws Exception {
        mockMvc.perform(post("/api/basic/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"age\": \"otuz\" }"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Okunamayan Istek"));
    }
}
