package com.gucardev.reportgenerationlighttaskwithjobrunr;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.gucardev.reportgenerationlighttaskwithjobrunr.tasks.*;
import com.gucardev.reportgenerationlighttaskwithjobrunr.tasks.handler.ReportGenerationHandler;
import com.gucardev.reportgenerationlighttaskwithjobrunr.mail.ReportMailer;
import com.gucardev.reportgenerationlighttaskwithjobrunr.report.*;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.jobrunr.jobs.lambdas.IocJobLambda;
import org.jobrunr.jobs.states.StateName;
import org.jobrunr.scheduling.JobScheduler;
import org.jobrunr.storage.JobNotFoundException;
import org.jobrunr.storage.StorageProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(properties = {
        "tasks.dispatch-interval=PT10S",
        "jobrunr.jobs.retry-back-off-time-seed=1"
})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ReportFlowTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(45);

    @Autowired MockMvc mvc;
    @Autowired JsonMapper json;
    @Autowired ReportService service;
    @Autowired ReportRepository reports;
    @Autowired ReportRequestRepository requests;
    @Autowired TaskDispatcher dispatcher;
    @Autowired StorageProvider storage;
    @Autowired PlatformTransactionManager transactionManager;
    @MockitoSpyBean TaskSubmissionRepository submissions;
    @MockitoSpyBean JobScheduler scheduler;
    @MockitoSpyBean ReportGenerationHandler jobs;
    @MockitoSpyBean ReportMailer mailer;

    private ReportService.ReportRequested request() {
        var result = service.request("MONTHLY_SALES", UUID.randomUUID() + "@example.com");
        dispatcher.tryDispatch(result.jobId());
        return result;
    }

    private void awaitState(UUID id, StateName state) {
        await().atMost(TIMEOUT).ignoreException(JobNotFoundException.class)
                .untilAsserted(() -> assertThat(storage.getJobById(id).getState()).isEqualTo(state));
    }

    private UUID readyEmailId(Long requestId) {
        return TaskService.idFor("ready:" + requestId);
    }

    @AfterEach
    void finishOutstandingWorkBeforeResettingSpies() {
        reset(submissions, scheduler, jobs, mailer);
        dispatcher.dispatchPending();
        await().atMost(TIMEOUT).until(() -> submissions.findAll().stream().allMatch(s -> {
            try {
                return s.getSubmittedAt() != null && storage.getJobById(s.getId()).getState() == StateName.SUCCEEDED;
            } catch (JobNotFoundException e) {
                return s.getSubmittedAt() != null; // a retention test intentionally deleted this history
            }
        }));
    }

    @Test
    void apiRequestGeneratesReportAndRunsASeparateNotificationJob() throws Exception {
        doAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return call.callRealMethod();
        }).when(jobs).handle(anyLong());
        String response = mvc.perform(post("/api/reports").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reportType":"MONTHLY_SALES","requestedBy":"finance@example.com"}"""))
                .andExpect(status().isAccepted()).andExpect(header().exists("Location"))
                .andReturn().getResponse().getContentAsString();
        var requested = json.readTree(response);
        long id = requested.get("reportRequestId").asLong();
        UUID jobId = UUID.fromString(requested.get("jobId").asString());
        awaitState(jobId, StateName.SUCCEEDED);
        UUID emailId = readyEmailId(id);
        awaitState(emailId, StateName.SUCCEEDED);

        mvc.perform(get("/api/reports/" + id)).andExpect(jsonPath("$.ready").value(true))
                .andExpect(jsonPath("$.content").value("MONTHLY_SALES report for finance@example.com"));
        mvc.perform(get("/api/jobs/" + jobId)).andExpect(jsonPath("$.state").value("SUCCEEDED"));
        verify(mailer).send(eq("finance@example.com"), any(UUID.class), eq(emailId.toString()));
    }

    @Test
    void transactionRollbackCreatesNeitherRequestNorJob() {
        var rolledBack = new TransactionTemplate(transactionManager).execute(status -> {
            var result = service.request("MONTHLY_SALES", UUID.randomUUID() + "@example.com");
            status.setRollbackOnly();
            return result;
        });
        assertThat(requests.findById(rolledBack.reportRequestId())).isEmpty();
        assertThat(submissions.findById(rolledBack.jobId())).isEmpty();
        assertThatThrownBy(() -> storage.getJobById(rolledBack.jobId())).isInstanceOf(JobNotFoundException.class);
    }

    @Test
    void recurringJobRecoversHandoffAfterJobRunrWasUnavailable() {
        doThrow(new IllegalStateException("Job storage temporarily unavailable"))
                .when(scheduler).enqueue(any(UUID.class), any(IocJobLambda.class));
        var result = request(); // The committed request remains accepted even when immediate enqueue fails.
        assertThat(submissions.findById(result.jobId()).orElseThrow().getSubmittedAt()).isNull();
        assertThatThrownBy(() -> storage.getJobById(result.jobId())).isInstanceOf(JobNotFoundException.class);

        doCallRealMethod().when(scheduler).enqueue(any(UUID.class), any(IocJobLambda.class));
        // No manual dispatch: the registered recurring JobRunr job must pick this up.
        awaitState(result.jobId(), StateName.SUCCEEDED);
        assertThat(submissions.findById(result.jobId()).orElseThrow().getSubmittedAt()).isNotNull();
    }

    @Test
    void repeatingHandoffAfterLostAcknowledgementDoesNotCreateAnotherExecution() {
        doThrow(new IllegalStateException("Lost acknowledgement"))
                .when(submissions).markSubmitted(any(UUID.class), any(Instant.class));
        var result = request();
        assertThat(submissions.findById(result.jobId()).orElseThrow().getSubmittedAt()).isNull();
        assertThat(storage.getJobById(result.jobId())).isNotNull();

        reset(submissions); // Spring Data's interface method delegates to its repository proxy.
        dispatcher.dispatch(result.jobId());
        dispatcher.dispatch(result.jobId());
        awaitState(result.jobId(), StateName.SUCCEEDED);
        assertThat(storage.getJobById(result.jobId()).getJobStates().stream()
                .filter(s -> s.getName() == StateName.PROCESSING)).hasSize(1);
        verify(jobs, times(1)).handle(result.reportRequestId());
    }

    @Test
    void mailFailureIsRetriedByJobRunrWithoutRegeneratingTheReport() {
        doThrow(new IllegalStateException("Mail provider temporarily unavailable")).doCallRealMethod()
                .when(mailer).send(anyString(), any(UUID.class), anyString());
        var result = request();
        awaitState(result.jobId(), StateName.SUCCEEDED);
        UUID emailId = readyEmailId(result.reportRequestId());
        awaitState(emailId, StateName.SUCCEEDED);

        assertThat(storage.getJobById(emailId).getJobStates().stream()
                .filter(s -> s.getName() == StateName.FAILED)).hasSize(1);
        verify(jobs, times(1)).handle(result.reportRequestId());
        verify(mailer, times(2)).send(anyString(), any(UUID.class), eq(emailId.toString()));
    }

    @Test
    void missingBusinessDataFailsWithoutRetries() {
        UUID id = UUID.randomUUID();
        scheduler.<ReportGenerationHandler>enqueue(id, handler -> handler.handle(-1L));
        awaitState(id, StateName.FAILED);
        assertThat(storage.getJobById(id).getJobStates().stream()
                .filter(s -> s.getName() == StateName.SCHEDULED)).isEmpty();
        assertThat(storage.getJobById(id).getJobStates().stream()
                .filter(s -> s.getName() == StateName.PROCESSING)).hasSize(1);
    }

    @Test
    void reportResultRollsBackTogetherWithItsNotificationIntent() {
        var request = requests.save(ReportRequest.create("TEST", "rollback@example.com"));
        doThrow(new IllegalStateException("Cannot persist notification"))
                .when(submissions).save(any(TaskSubmission.class));
        assertThatThrownBy(() -> service.completeGeneration(request.getId(), "result"))
                .isInstanceOf(IllegalStateException.class);
        assertThat(reports.findByReportRequestId(request.getId())).isEmpty();
        assertThat(submissions.findById(readyEmailId(request.getId()))).isEmpty();
    }

    @Test
    void concurrentSharesHaveOneJobAndStayDeduplicatedAfterHistoryCleanup() throws Exception {
        var result = request();
        awaitState(result.jobId(), StateName.SUCCEEDED);
        UUID shareId;
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> service.share(result.reportRequestId(), "colleague@example.com"));
            var second = executor.submit(() -> service.share(result.reportRequestId(), "colleague@example.com"));
            shareId = first.get(10, TimeUnit.SECONDS).jobId();
            assertThat(second.get(10, TimeUnit.SECONDS).jobId()).isEqualTo(shareId);
        }
        awaitState(shareId, StateName.SUCCEEDED);
        verify(mailer, times(1)).send(eq("colleague@example.com"), any(UUID.class), eq(shareId.toString()));
        storage.deletePermanently(shareId);
        assertThat(service.share(result.reportRequestId(), "colleague@example.com").jobId()).isEqualTo(shareId);
        assertThatThrownBy(() -> storage.getJobById(shareId)).isInstanceOf(JobNotFoundException.class);
        mvc.perform(get("/api/jobs/" + shareId)).andExpect(jsonPath("$.state").value("UNAVAILABLE"));
    }

    @Test
    void concurrentRequestsCannotBypassThePerUserRateLimit() throws Exception {
        String user = UUID.randomUUID() + "@example.com";
        try (var executor = Executors.newFixedThreadPool(4)) {
            var calls = java.util.stream.IntStream.range(0, 4).mapToObj(i -> executor.submit(() -> {
                try {
                    service.request("SALES", user);
                    return 202;
                } catch (TooManyReportRequestsException e) {
                    return 429;
                }
            })).toList();
            var statuses = new java.util.ArrayList<Integer>();
            for (var call : calls) statuses.add(call.get(10, TimeUnit.SECONDS));
            assertThat(statuses).containsExactlyInAnyOrder(202, 202, 202, 429);
        }
        mvc.perform(post("/api/reports").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reportType\":\"SALES\",\"requestedBy\":\"" + user + "\"}"))
                .andExpect(status().isTooManyRequests()).andExpect(header().exists("Retry-After"));
    }

    @Test
    void invalidInputAndUnreadyReportsAreRejected() throws Exception {
        mvc.perform(post("/api/reports").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reportType":"","requestedBy":"invalid"}"""))
                .andExpect(status().isBadRequest());
        var pending = requests.save(ReportRequest.create("TEST", "pending@example.com"));
        mvc.perform(post("/api/reports/" + pending.getId() + "/share").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"recipientEmail":"colleague@example.com"}"""))
                .andExpect(status().isConflict());
        mvc.perform(get("/api/jobs/" + UUID.randomUUID())).andExpect(status().isNotFound());
    }
}
