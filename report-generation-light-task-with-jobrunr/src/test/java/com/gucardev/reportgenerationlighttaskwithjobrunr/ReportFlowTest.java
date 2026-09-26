package com.gucardev.reportgenerationlighttaskwithjobrunr;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.gucardev.reportgenerationlighttaskwithjobrunr.report.Report;
import com.gucardev.reportgenerationlighttaskwithjobrunr.report.ReportEmailSender;
import com.gucardev.reportgenerationlighttaskwithjobrunr.report.ReportRepository;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import org.jobrunr.jobs.states.StateName;
import org.jobrunr.storage.JobNotFoundException;
import org.jobrunr.storage.StorageProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

// A small retry seed keeps JobRunr's exponential backoff at seconds instead of minutes.
@SpringBootTest(properties = "jobrunr.jobs.retry-back-off-time-seed=1")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ReportFlowTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(60);

    @Autowired MockMvc mvc;
    @Autowired JsonMapper json;
    @Autowired ReportRepository reports;
    @Autowired StorageProvider storage;
    @MockitoBean ReportEmailSender emailSender;

    @Test
    void requestedReportIsGeneratedAndOwnerIsEmailedOnce() throws Exception {
        String owner = uniqueEmail();
        long id = requestReport(owner);

        awaitReady(id);
        awaitSucceeded(reportReadyEmailJobId(id));
        mvc.perform(get("/api/reports/" + id))
                .andExpect(jsonPath("$.content").value("MONTHLY_SALES report for " + owner));
        verify(emailSender, times(1)).sendReportReadyEmail(id);
    }

    @Test
    void failedEmailIsRetriedWithoutRegeneratingTheReport() throws Exception {
        doThrow(new IllegalStateException("Mail provider down")).doNothing()
                .when(emailSender).sendReportReadyEmail(anyLong());

        long id = requestReport(uniqueEmail());

        awaitSucceeded(reportReadyEmailJobId(id));
        verify(emailSender, times(2)).sendReportReadyEmail(id);
        awaitSucceeded(generateReportJobId(id));
        var report = reports.findById(id).orElseThrow();
        assertThat(report.getStatus()).isEqualTo(Report.Status.READY);
        // The generate job ran once, so the report was marked ready exactly once.
        assertThat(storage.getJobById(generateReportJobId(id)).getJobStates())
                .filteredOn(state -> state.getName() == StateName.PROCESSING).hasSize(1);
    }

    private long requestReport(String owner) throws Exception {
        String response = mvc.perform(post("/api/reports").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reportType":"MONTHLY_SALES","requestedBy":"%s"}""".formatted(owner)))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(response).get("reportId").asLong();
    }

    /** Mirror ReportJobScheduler's fixed job ids. */
    private static UUID generateReportJobId(long reportId) {
        return jobId("generate-report:" + reportId);
    }

    private static UUID reportReadyEmailJobId(long reportId) {
        return jobId("report-ready-email:" + reportId);
    }

    private static UUID jobId(String key) {
        return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8));
    }

    private void awaitReady(long id) {
        await().atMost(TIMEOUT).untilAsserted(() ->
                mvc.perform(get("/api/reports/" + id)).andExpect(jsonPath("$.status").value("READY")));
    }

    private void awaitSucceeded(UUID jobId) {
        await().atMost(TIMEOUT).ignoreException(JobNotFoundException.class)
                .until(() -> storage.getJobById(jobId).getState() == StateName.SUCCEEDED);
    }

    private static String uniqueEmail() {
        return UUID.randomUUID() + "@example.com";
    }
}
