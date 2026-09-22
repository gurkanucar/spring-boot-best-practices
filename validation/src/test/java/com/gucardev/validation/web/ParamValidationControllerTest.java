package com.gucardev.validation.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gucardev.validation.config.LocaleConfig;
import com.gucardev.validation.error.ProblemDetailFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ParamValidationController.class)
@Import({LocaleConfig.class, ProblemDetailFactory.class})
class ParamValidationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void acceptsValidPathVariable() throws Exception {
        mockMvc.perform(get("/api/params/users/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7));
    }

    @Test
    void rejectsPathVariableBelowMinimum() throws Exception {
        mockMvc.perform(get("/api/params/users/0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Dogrulama Hatasi"))
                .andExpect(jsonPath("$.errors.length()").value(1))
                .andExpect(jsonPath("$.errors[0].field").value("id"))
                .andExpect(jsonPath("$.errors[0].code").value("Min"));
    }

    @Test
    void acceptsValidRequestParam() throws Exception {
        mockMvc.perform(get("/api/params/users/search").param("email", "ayse@example.com"))
                .andExpect(status().isOk());
    }

    @Test
    void rejectsInvalidRequestParam() throws Exception {
        mockMvc.perform(get("/api/params/users/search").param("email", "gecersiz"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("email"))
                .andExpect(jsonPath("$.errors[0].code").value("Email"))
                .andExpect(jsonPath("$.errors[0].message").value("Gecerli bir e-posta adresi giriniz"));
    }
}
