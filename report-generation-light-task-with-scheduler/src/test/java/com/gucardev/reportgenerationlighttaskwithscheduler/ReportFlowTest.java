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
        long id = requestReport();

        await().atMost(TIMEOUT).until(() -> getReport(id).get("ready").asBoolean());
        awaitTaskSucceeded("generate-report:" + id);
        awaitTaskSucceeded("report-ready-email:" + id);
        verify(reportEmailSender, times(1)).sendReportReadyEmail(id);
    }

    @Test
    void failedEmailIsRetried() throws Exception {
        doThrow(new IllegalStateException("SMTP down")).doNothing()
                .when(reportEmailSender).sendReportReadyEmail(anyLong());

        long id = requestReport();

        Map<String, Object> email = awaitTaskSucceeded("report-ready-email:" + id);
        assertThat(email.get("attempts")).isEqualTo(2);
        assertThat((String) email.get("last_error")).contains("SMTP down");
        verify(reportEmailSender, times(2)).sendReportReadyEmail(id);
        assertThat(jdbc.sql("select count(*) from report where report_request_id = :id")
                .param("id", id).query(Long.class).single()).isEqualTo(1);
    }

    private long requestReport() throws Exception {
        ResultActions result = mockMvc.perform(post("/api/reports").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reportType": "MONTHLY_SALES", "requestedBy": "%s"}""".formatted(OWNER)))
                .andExpect(status().isAccepted());
        return json(result).get("reportRequestId").asLong();
    }

    private JsonNode getReport(long id) throws Exception {
        return json(mockMvc.perform(get("/api/reports/" + id)).andExpect(status().isOk()));
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
