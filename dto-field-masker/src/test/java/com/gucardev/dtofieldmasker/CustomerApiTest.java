package com.gucardev.dtofieldmasker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gucardev.dtofieldmasker.customer.CustomerRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class CustomerApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CustomerRepository customerRepository;

    @Test
    void listMatchesTheExpectedMaskedJson() throws Exception {
        mockMvc.perform(get("/api/customers"))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        [
                          {
                            "name": "Gurkan",
                            "idNumber": "xxxx56789",
                            "accounts": [
                              {"accountName": "account1", "accountNumber": "12345**********"},
                              {"accountName": "account2", "accountNumber": "123456**********"}
                            ]
                          },
                          {
                            "name": "Mehmet",
                            "idNumber": "xxxx54321",
                            "accounts": [
                              {"accountName": "account3", "accountNumber": "12345678**********"},
                              {"accountName": "account4", "accountNumber": "123456789**********"}
                            ]
                          }
                        ]
                        """, true));
    }

    @Test
    void singleCustomerIsMaskedToo() throws Exception {
        mockMvc.perform(get("/api/customers/2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idNumber").value("xxxx54321"))
                .andExpect(jsonPath("$.accounts[1].accountNumber").value("123456789**********"));
        mockMvc.perform(get("/api/customers/99")).andExpect(status().isNotFound());
    }

    @Test
    void responseNeverContainsTheRealValues() throws Exception {
        String body = mockMvc.perform(get("/api/customers")).andReturn().getResponse().getContentAsString();

        // Compare whole JSON string values: a masked value may legitimately start with digits that
        // are also the beginning of another real value (e.g. "123456789**********").
        customerRepository.findAll().forEach(customer -> {
            assertThat(body).doesNotContain("\"" + customer.idNumber() + "\"");
            customer.accounts().forEach(a -> assertThat(body).doesNotContain("\"" + a.accountNumber() + "\""));
        });
    }

    @Test
    void domainObjectsStillHoldTheRealValues() {
        // masking happens while writing JSON only; the data itself is untouched
        assertThat(customerRepository.findAll().getFirst().idNumber()).isEqualTo("123456789");
    }

    @Test
    void showcaseShowsEveryOptionOnTheSameInput() throws Exception {
        mockMvc.perform(get("/api/showcase"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.original").value("1234567890123456"))
                .andExpect(jsonPath("$.defaults").value("xxxxxxxxxxxxx456"))
                .andExpect(jsonPath("$.firstXClear").value("1234xxxxxxxxxxxx"))
                .andExpect(jsonPath("$.firstXMasked").value("xxxx567890123456"))
                .andExpect(jsonPath("$.lastXClear").value("xxxxxxxxxxxx3456"))
                .andExpect(jsonPath("$.lastXMasked").value("123456789012xxxx"))
                .andExpect(jsonPath("$.customReplaceChar").value("************3456"))
                .andExpect(jsonPath("$.multiCharReplacement").value("123456789012[#][#][#][#]"))
                .andExpect(jsonPath("$.shortValueFailsClosed").value("xxx"));
    }
}
