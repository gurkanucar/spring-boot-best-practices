package com.gucardev.reportgenerationlighttaskwithscheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

class ReportRateLimitTest extends IntegrationTestBase {

    private ResultActions export(String user) throws Exception {
        return mockMvc.perform(post("/api/reports").contentType(MediaType.APPLICATION_JSON).content("""
                {"reportType": "CUSTOMER_LIST", "requestedBy": "%s"}""".formatted(user)));
    }

    @Test
    void atMostThreeReportsPerUserInTenMinutes() throws Exception {
        for (int i = 0; i < 3; i++) {
            export("alice@example.com").andExpect(status().isAccepted());
        }

        export("alice@example.com")
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.startsWith(
                        "At most 3 reports per 10 minutes. Try again in")));
        // Another user is not affected.
        export("bob@example.com").andExpect(status().isAccepted());

        assertThat(jdbc.sql("select count(*) from report_request where requested_by = 'alice@example.com'")
                .query(Long.class).single()).isEqualTo(3);
    }

    @Test
    void theLimitIsASlidingWindow() throws Exception {
        for (int i = 0; i < 3; i++) {
            export("carol@example.com").andExpect(status().isAccepted());
        }
        // The oldest request is now 11 minutes old: one slot is free again.
        jdbc.sql("""
                update report_request set created_at = now() - interval '11 minutes'
                where id = (select id from report_request where requested_by = 'carol@example.com'
                            order by created_at limit 1)""").update();

        export("carol@example.com").andExpect(status().isAccepted());
        export("carol@example.com").andExpect(status().isTooManyRequests());
    }
}
