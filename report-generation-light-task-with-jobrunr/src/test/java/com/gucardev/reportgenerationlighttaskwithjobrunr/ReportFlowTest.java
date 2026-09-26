package com.gucardev.reportgenerationlighttaskwithjobrunr;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.gucardev.reportgenerationlighttaskwithjobrunr.report.*;
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
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.json.JsonMapper;

// A small retry seed keeps JobRunr's exponential backoff at seconds instead of minutes.
@SpringBootTest(properties = "jobrunr.jobs.retry-back-off-time-seed=1")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ReportFlowTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(60);

    @Autowired MockMvc mvc;
    @Autowired JsonMapper json;
    @Autowired ReportRequestRepository requests;
    @Autowired ReportRepository reports;
    @Autowired StorageProvider storage;
    @MockitoBean ReportMailer mailer;

    @Test
    void requestedReportIsGeneratedAndOwnerIsEmailedOnce() throws Exception {
        String owner = uniqueEmail();
        long id = requestReport(owner);

        awaitReady(id);
        UUID readyJob = ReportJobs.jobId("ready:" + id);
        awaitSucceeded(readyJob);
        mvc.perform(get("/api/reports/" + id))
                .andExpect(jsonPath("$.content").value("MONTHLY_SALES report for " + owner));
        verify(mailer, times(1)).send(eq(owner), any(UUID.class), eq(readyJob.toString()));
    }

    @Test
    void failedEmailIsRetriedWithoutRegeneratingTheReport() throws Exception {
        String owner = uniqueEmail();
        doThrow(new IllegalStateException("Mail provider down")).doNothing()
                .when(mailer).send(eq(owner), any(UUID.class), anyString());

        long id = requestReport(owner);

        UUID readyJob = ReportJobs.jobId("ready:" + id);
        awaitSucceeded(readyJob);
        verify(mailer, times(2)).send(eq(owner), any(UUID.class), eq(readyJob.toString()));
        assertThat(reports.findAll()).filteredOn(r -> r.getReportRequestId() == id).hasSize(1);
    }

    @Test
    void sharingWaitsForTheReportAndTheSameRecipientGetsOneJob() throws Exception {
        var pending = requests.save(ReportRequest.create("MONTHLY_SALES", uniqueEmail())); // no job: never ready
        share(pending.getId(), "colleague@example.com").andExpect(status().isConflict());

        long id = requestReport(uniqueEmail());
        awaitReady(id);
        String recipient = uniqueEmail();
        UUID first = shareJobId(id, recipient);
        UUID second = shareJobId(id, recipient);

        assertThat(second).isEqualTo(first);
        awaitSucceeded(first);
        verify(mailer, times(1)).send(eq(recipient), any(UUID.class), eq(first.toString()));
    }

    private long requestReport(String owner) throws Exception {
        String response = mvc.perform(post("/api/reports").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reportType":"MONTHLY_SALES","requestedBy":"%s"}""".formatted(owner)))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(response).get("reportRequestId").asLong();
    }

    private ResultActions share(long id, String recipient) throws Exception {
        return mvc.perform(post("/api/reports/" + id + "/share").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"recipientEmail":"%s"}""".formatted(recipient)));
    }

    private UUID shareJobId(long id, String recipient) throws Exception {
        String response = share(id, recipient).andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(json.readTree(response).get("jobId").asString());
    }

    private void awaitReady(long id) {
        await().atMost(TIMEOUT).untilAsserted(() ->
                mvc.perform(get("/api/reports/" + id)).andExpect(jsonPath("$.ready").value(true)));
    }

    private void awaitSucceeded(UUID jobId) {
        await().atMost(TIMEOUT).ignoreException(JobNotFoundException.class)
                .until(() -> storage.getJobById(jobId).getState() == StateName.SUCCEEDED);
    }

    private static String uniqueEmail() {
        return UUID.randomUUID() + "@example.com";
    }
}
