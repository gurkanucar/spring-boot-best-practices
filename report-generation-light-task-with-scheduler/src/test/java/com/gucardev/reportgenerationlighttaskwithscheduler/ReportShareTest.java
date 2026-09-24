package com.gucardev.reportgenerationlighttaskwithscheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gucardev.reportgenerationlighttaskwithscheduler.report.ReportRepository;
import com.gucardev.reportgenerationlighttaskwithscheduler.tasks.entity.BackgroundTask;
import com.gucardev.reportgenerationlighttaskwithscheduler.tasks.entity.TaskStatus;
import com.gucardev.reportgenerationlighttaskwithscheduler.tasks.entity.TaskType;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.json.JsonMapper;

class ReportShareTest extends IntegrationTestBase {

    @Autowired
    private JsonMapper jsonMapper;
    @Autowired
    private ReportRepository reports;

    private long export() throws Exception {
        String response = mockMvc.perform(post("/api/reports").contentType(MediaType.APPLICATION_JSON).content("""
                        {"reportType": "SALES_BY_REGION", "requestedBy": "ayse@example.com"}"""))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        return jsonMapper.readTree(response).get("reportRequestId").asLong();
    }

    private ResultActions share(long reportRequestId, String recipient) throws Exception {
        return mockMvc.perform(post("/api/reports/" + reportRequestId + "/share")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"recipientEmail": "%s"}""".formatted(recipient)));
    }

    private String storedPayload(UUID taskId) {
        return jdbc.sql("select payload::text from background_task where id = :id")
                .param("id", taskId).query(String.class).single();
    }

    @Test
    void sharingAFinishedReportRunsARecordPayloadTask() throws Exception {
        long reportRequestId = export();
        await().atMost(TIMEOUT).until(() -> reports.existsByReportRequestId(reportRequestId));
        UUID reportId = reports.findByReportRequestId(reportRequestId).orElseThrow().getId();

        String response = share(reportRequestId, "mehmet@example.com")
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        UUID taskId = UUID.fromString(jsonMapper.readTree(response).get("taskId").asString());
        awaitStatus(taskId, TaskStatus.SUCCEEDED);

        // Clicking "share" again for the same person: same task, no second email.
        share(reportRequestId, "mehmet@example.com").andExpect(jsonPath("$.taskId").value(taskId.toString()));

        // What the payload column holds for the three handler styles:
        BackgroundTask generation = tasks.findByIdempotencyKey("report-generation:" + reportRequestId).orElseThrow();
        BackgroundTask email = tasks.findByIdempotencyKey("report-ready-email:" + reportRequestId).orElseThrow();
        assertThat(storedPayload(generation.getId())).isEqualTo(String.valueOf(reportRequestId)); // TaskHandler<Long>
        assertThat(storedPayload(email.getId())).isEqualTo("\"" + reportId + "\"");                  // TaskHandler<UUID>
        assertThat(jsonMapper.readTree(storedPayload(taskId)))                                        // TaskHandler<Payload>
                .isEqualTo(jsonMapper.readTree("""
                        {"reportId": "%s", "recipientEmail": "mehmet@example.com"}""".formatted(reportId)));
    }

    @Test
    void aReportThatIsNotReadyCannotBeShared() throws Exception {
        share(987_654_321L, "mehmet@example.com")
                .andExpect(status().isConflict());
    }

    @Test
    void recordPayloadWithAMissingFieldIsDeadAtOnce() {
        UUID id = taskService.enqueue(TaskType.REPORT_SHARE, Map.of("reportId", UUID.randomUUID()), null).getId();

        BackgroundTask task = awaitStatus(id, TaskStatus.DEAD);
        assertThat(task.getAttempts()).isEqualTo(1);
        assertThat(task.getLastError()).contains("recipientEmail");
    }
}
