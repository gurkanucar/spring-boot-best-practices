package com.gucardev.reportgenerationlighttaskwithscheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class ReportFlowTest extends IntegrationTestBase {

    private static final String OWNER = "owner@example.com";

    @Autowired
    private JsonMapper jsonMapper;

    @Test
    void reportIsGeneratedAndTheOwnerIsEmailedOnce() throws Exception {
        long reportId = requestReport();

        await().atMost(TIMEOUT).until(() -> "READY".equals(getReport(reportId).get("status").asString()));
        assertThat(getReport(reportId).get("content").asString()).isEqualTo("MONTHLY_SALES report for " + OWNER);
        awaitTaskSucceeded("generate-report:" + reportId);
        awaitTaskSucceeded("report-ready-email:" + reportId);
        verify(reportEmailSender, times(1)).sendReportReadyEmail(reportId);
    }

    @Test
    void failedEmailIsRetried() throws Exception {
        doThrow(new IllegalStateException("SMTP down")).doNothing()
                .when(reportEmailSender).sendReportReadyEmail(anyLong());

        long reportId = requestReport();

        Map<String, Object> email = awaitTaskSucceeded("report-ready-email:" + reportId);
        assertThat(email.get("attempts")).isEqualTo(2);
        assertThat((String) email.get("last_error")).contains("SMTP down");
        verify(reportEmailSender, times(2)).sendReportReadyEmail(reportId);
        assertThat(jdbc.sql("select count(*) from background_task where type = 'SEND_REPORT_READY_EMAIL'")
                .query(Long.class).single()).isEqualTo(1);
    }

    private long requestReport() throws Exception {
        ResultActions result = mockMvc.perform(post("/api/reports").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reportType": "MONTHLY_SALES", "requestedBy": "%s"}""".formatted(OWNER)))
                .andExpect(status().isAccepted());
        return json(result).get("reportId").asLong();
    }

    private JsonNode getReport(long reportId) throws Exception {
        return json(mockMvc.perform(get("/api/reports/" + reportId)).andExpect(status().isOk()));
    }

    private Map<String, Object> awaitTaskSucceeded(String idempotencyKey) {
        await().atMost(TIMEOUT).until(() -> "SUCCEEDED".equals(task(idempotencyKey).get("status")));
        return task(idempotencyKey);
    }

    private Map<String, Object> task(String idempotencyKey) {
        return jdbc.sql("select status, attempts, last_error from background_task where idempotency_key = :key")
                .param("key", idempotencyKey).query().listOfRows().stream().findFirst().orElse(Map.of());
    }

    private JsonNode json(ResultActions result) throws Exception {
        return jsonMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }
}
