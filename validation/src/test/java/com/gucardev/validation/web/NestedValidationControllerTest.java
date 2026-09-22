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

@WebMvcTest(NestedValidationController.class)
@Import({LocaleConfig.class, ProblemDetailFactory.class})
class NestedValidationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void acceptsFullyValidNestedRequest() throws Exception {
        mockMvc.perform(post("/api/nested/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName": "Ayse Yilmaz",
                                  "address": { "city": "Ankara", "district": "Cankaya", "postalCode": "06500" },
                                  "contacts": [ { "type": "EMAIL", "value": "ayse@example.com" } ]
                                }
                                """))
                .andExpect(status().isOk());
    }

    @Test
    void reportsNestedFieldWithDottedPath() throws Exception {
        mockMvc.perform(post("/api/nested/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName": "Ayse Yilmaz",
                                  "address": { "city": "", "district": "Cankaya", "postalCode": "123" },
                                  "contacts": [ { "type": "EMAIL", "value": "ayse@example.com" } ]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.length()").value(2))
                .andExpect(jsonPath("$.errors[?(@.field == 'address.city')].code").value("NotBlank"))
                .andExpect(jsonPath("$.errors[?(@.field == 'address.postalCode')].code").value("Pattern"));
    }

    @Test
    void reportsCollectionElementWithIndexedPath() throws Exception {
        mockMvc.perform(post("/api/nested/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName": "Ayse Yilmaz",
                                  "address": { "city": "Ankara", "district": "Cankaya", "postalCode": "06500" },
                                  "contacts": [
                                    { "type": "EMAIL", "value": "ayse@example.com" },
                                    { "type": "TELEFON", "value": "" }
                                  ]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field == 'contacts[1].value')].code").value("NotBlank"))
                .andExpect(jsonPath("$.errors[?(@.field == 'contacts[1].type')].code").value("EnumValue"));
    }

    @Test
    void rejectsMissingAddressAndEmptyContacts() throws Exception {
        mockMvc.perform(post("/api/nested/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "fullName": "Ayse Yilmaz", "contacts": [] }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field == 'address')].code").value("NotNull"))
                .andExpect(jsonPath("$.errors[?(@.field == 'contacts')].code").value("NotEmpty"));
    }

    @Test
    void validatesEveryElementOfBulkRequest() throws Exception {
        mockMvc.perform(post("/api/nested/users/bulk")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                [
                                  {
                                    "fullName": "Ayse Yilmaz",
                                    "address": { "city": "Ankara", "district": "Cankaya", "postalCode": "06500" },
                                    "contacts": [ { "type": "EMAIL", "value": "ayse@example.com" } ]
                                  },
                                  {
                                    "fullName": "",
                                    "address": { "city": "Izmir", "district": "Konak", "postalCode": "35000" },
                                    "contacts": [ { "type": "EMAIL", "value": "ali@example.com" } ]
                                  }
                                ]
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.length()").value(1))
                .andExpect(jsonPath("$.errors[0].field").value("requests[1].fullName"));
    }
}
