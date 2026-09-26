package com.gucardev.reportgenerationlighttaskwithscheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gucardev.reportgenerationlighttaskwithscheduler.report.ReportRequest;
import com.gucardev.reportgenerationlighttaskwithscheduler.report.ReportRequestRepository;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class ReportFlowTest extends IntegrationTestBase {

    private static final String OWNER = "owner@example.com";
    private static final String COLLEAGUE = "colleague@example.com";

    @Autowired
    private JsonMapper jsonMapper;
    @Autowired
    private ReportRequestRepository requests;

    @Test
    void reportIsGeneratedAndTheOwnerIsEmailedOnce() throws Exception {
        long id = requestReport();
        UUID reportId = awaitReady(id);

        awaitTaskSucceeded("report-ready-email:" + id);
        verify(mailer, times(1)).send(OWNER, reportId, "report-ready:" + reportId);
    }

    @Test
    void failedEmailIsRetried() throws Exception {
        doThrow(new IllegalStateException("SMTP down")).doNothing().when(mailer).send(eq(OWNER), any(), any());

        long id = requestReport();

        Map<String, Object> email = awaitTaskSucceeded("report-ready-email:" + id);
        assertThat(email.get("attempts")).isEqualTo(2);
        assertThat((String) email.get("last_error")).contains("SMTP down");
        verify(mailer, times(2)).send(eq(OWNER), any(), any());
        assertThat(jdbc.sql("select count(*) from report where report_request_id = :id")
                .param("id", id).query(Long.class).single()).isEqualTo(1);
    }

    @Test
    void reportIsSharedOncePerRecipient() throws Exception {
        long notReady = requests.save(ReportRequest.create("MONTHLY_SALES", OWNER)).getId();
        share(notReady).andExpect(status().isConflict());

        long id = requestReport();
        UUID reportId = awaitReady(id);
        String first = json(share(id).andExpect(status().isAccepted())).get("taskId").asString();
        String second = json(share(id).andExpect(status().isAccepted())).get("taskId").asString();

        assertThat(second).isEqualTo(first);
        awaitTaskSucceeded("report-share:" + reportId + ":" + COLLEAGUE);
        verify(mailer, times(1)).send(eq(COLLEAGUE), eq(reportId), any());
    }

    private long requestReport() throws Exception {
        ResultActions result = mockMvc.perform(post("/api/reports").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reportType": "MONTHLY_SALES", "requestedBy": "%s"}""".formatted(OWNER)))
                .andExpect(status().isAccepted());
        return json(result).get("reportRequestId").asLong();
    }

    private ResultActions share(long id) throws Exception {
        return mockMvc.perform(post("/api/reports/" + id + "/share").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"recipientEmail": "%s"}""".formatted(COLLEAGUE)));
    }

    private UUID awaitReady(long id) {
        await().atMost(TIMEOUT).until(() -> json(mockMvc.perform(get("/api/reports/" + id))).get("ready").asBoolean());
        return jdbc.sql("select id from report where report_request_id = :id").param("id", id)
                .query(UUID.class).single();
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
