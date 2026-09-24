package com.gucardev.reportgenerationlighttaskwithscheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gucardev.reportgenerationlighttaskwithscheduler.tasks.entity.BackgroundTask;
import com.gucardev.reportgenerationlighttaskwithscheduler.tasks.entity.TaskStatus;
import com.gucardev.reportgenerationlighttaskwithscheduler.tasks.entity.TaskType;
import com.gucardev.reportgenerationlighttaskwithscheduler.tasks.exception.NonRetryableTaskException;
import com.gucardev.reportgenerationlighttaskwithscheduler.tasks.handler.ReportGenerationHandler;
import com.gucardev.reportgenerationlighttaskwithscheduler.tasks.scheduler.StuckTaskRecovery;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class BackgroundTaskTest extends IntegrationTestBase {

    @Autowired
    private StuckTaskRecovery recovery;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private JsonMapper jsonMapper;

    /** Identity values start at 1, so a large random id never exists. */
    private static long missingReportRequestId() {
        return ThreadLocalRandom.current().nextLong(1_000_000_000L, Long.MAX_VALUE);
    }

    private BackgroundTask enqueueReport() {
        return taskService.enqueue(TaskType.REPORT_GENERATION, missingReportRequestId(), null);
    }

    // ---------------------------------------------------------------- happy path

    @Test
    void enqueuedTaskIsPickedUpByThePollerAndSucceeds() throws Exception {
        String response = mockMvc.perform(post("/api/reports").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reportType": "MONTHLY_SALES", "requestedBy": "finance@example.com"}"""))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        JsonNode requested = jsonMapper.readTree(response);
        long reportRequestId = requested.get("reportRequestId").asLong();
        UUID taskId = UUID.fromString(requested.get("taskId").asString());

        BackgroundTask task = awaitStatus(taskId, TaskStatus.SUCCEEDED);
        assertThat(task.getAttempts()).isEqualTo(1);
        assertThat(task.getLockedBy()).isNull();
        assertThat(task.getLockedAt()).isNull();

        mockMvc.perform(get("/api/reports/" + reportRequestId))
                .andExpect(jsonPath("$.ready").value(true))
                .andExpect(jsonPath("$.content").value("MONTHLY_SALES report for finance@example.com"));

        // The report handler enqueued the email task in its own transaction; it runs too.
        BackgroundTask email = tasks.findByIdempotencyKey("report-ready-email:" + reportRequestId).orElseThrow();
        assertThat(email.getType()).isEqualTo(TaskType.EMAIL_SEND);
        awaitStatus(email.getId(), TaskStatus.SUCCEEDED);
    }

    // ---------------------------------------------------------------- failures

    @Test
    void failingHandlerPutsTheTaskBackToPendingWithBackoff() {
        doThrow(new IllegalStateException("Reporting database unavailable")).when(reportHandler).handle(any());
        UUID id = enqueueReport().getId();

        await().atMost(TIMEOUT).untilAsserted(() -> assertThat(task(id).getAttempts()).isEqualTo(1));
        BackgroundTask task = awaitStatus(id, TaskStatus.PENDING);
        assertThat(task.getAttempts()).isEqualTo(1);
        assertThat(task.getRunAt()).isBetween(Instant.now().plusSeconds(20), Instant.now().plusSeconds(31));
        assertThat(task.getLastError()).startsWith("java.lang.IllegalStateException: Reporting database unavailable");
        assertThat(task.getLockedBy()).isNull();
    }

    @Test
    void nonRetryableFailureIsDeadAtOnce() {
        // The real handler: the report request does not exist, retrying cannot help.
        UUID id = enqueueReport().getId();

        BackgroundTask task = awaitStatus(id, TaskStatus.DEAD);
        assertThat(task.getAttempts()).isEqualTo(1);
        assertThat(task.getLastError()).contains("NonRetryableTaskException").contains("does not exist");
    }

    @Test
    void payloadThatDoesNotFitTheHandlersTypeIsDeadAtOnce() {
        UUID missing = taskService.enqueue(TaskType.EMAIL_SEND, null, null).getId();
        UUID wrongType = taskService.enqueue(TaskType.EMAIL_SEND, 42L, null).getId();
        UUID notAUuid = taskService.enqueue(TaskType.EMAIL_SEND, "not-a-uuid", null).getId();

        for (UUID id : List.of(missing, wrongType, notAUuid)) {
            BackgroundTask task = awaitStatus(id, TaskStatus.DEAD);
            assertThat(task.getAttempts()).isEqualTo(1);
            assertThat(task.getLastError()).contains("Unreadable payload for EMAIL_SEND");
        }
    }

    @Test
    void failureOnTheLastAttemptIsDead() {
        doThrow(new IllegalStateException("Still failing")).when(reportHandler).handle(any());
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                        insert into background_task (id, type, payload, status, attempts, max_attempts, run_at)
                        values (:id, 'REPORT_GENERATION', cast(:payload as jsonb), 'PENDING', 2, 3, now())""")
                .param("id", id).param("payload", String.valueOf(missingReportRequestId())).update();

        BackgroundTask task = awaitStatus(id, TaskStatus.DEAD);
        assertThat(task.getAttempts()).isEqualTo(3);
        assertThat(task.getLastError()).contains("Still failing");
    }

    // ---------------------------------------------------------------- concurrency

    @Test
    void perTypeConcurrencyLimitIsRespected() throws Exception {
        var running = new AtomicInteger();
        var maxRunning = new AtomicInteger();
        var twoStarted = new CountDownLatch(2);
        var release = new CountDownLatch(1);
        doAnswer(invocation -> {
            maxRunning.accumulateAndGet(running.incrementAndGet(), Math::max);
            twoStarted.countDown();
            try {
                release.await(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
            } finally {
                running.decrementAndGet();
            }
            return null;
        }).when(reportHandler).handle(any());

        List<UUID> ids = IntStream.range(0, 5).mapToObj(i -> enqueueReport().getId()).toList();

        assertThat(twoStarted.await(TIMEOUT.toSeconds(), TimeUnit.SECONDS)).isTrue();
        // Give the poller several rounds to (wrongly) start a third one.
        Thread.sleep(2_500);
        assertThat(running.get()).isEqualTo(2);
        release.countDown();

        for (UUID id : ids) {
            // Tasks put back for lack of a slot did not use up an attempt.
            assertThat(awaitStatus(id, TaskStatus.SUCCEEDED).getAttempts()).isEqualTo(1);
        }
        assertThat(maxRunning.get()).isEqualTo(2);
    }

    // ---------------------------------------------------------------- enqueue guarantees

    @Test
    void idempotencyKeyPreventsDuplicateTasks() {
        UUID payload = UUID.randomUUID();
        BackgroundTask first = taskService.enqueue(TaskType.EMAIL_SEND, payload, "welcome-email:42");
        BackgroundTask second = taskService.enqueue(TaskType.EMAIL_SEND, payload, "welcome-email:42");

        assertThat(second.getId()).isEqualTo(first.getId());
        assertThat(jdbc.sql("select count(*) from background_task where idempotency_key = 'welcome-email:42'")
                .query(Long.class).single()).isEqualTo(1);
    }

    @Test
    void taskCommitsOnlyTogetherWithTheCallersTransaction() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            taskService.enqueue(TaskType.EMAIL_SEND, UUID.randomUUID(),
                    "rolled-back");
            status.setRollbackOnly(); // e.g. the business data failed to save
        });

        assertThat(tasks.findByIdempotencyKey("rolled-back")).isEmpty();
    }

    // ---------------------------------------------------------------- recovery

    @Test
    void stalledRunningTaskIsRecoveredWithoutUsingAnAttempt() {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                        insert into background_task (id, type, payload, status, attempts, run_at, locked_at, locked_by)
                        values (:id, 'EMAIL_SEND', cast(:payload as jsonb), 'RUNNING', 2, now() - interval '31 minutes',
                                now() - interval '30 minutes', 'crashed-instance')""")
                .param("id", id)
                .param("payload", "\"" + UUID.randomUUID() + "\"")
                .update();

        assertThat(recovery.recoverStuckTasks()).isEqualTo(1);

        // Picked up again: 2 (before the stall) + 1 (this run).
        BackgroundTask task = awaitStatus(id, TaskStatus.SUCCEEDED);
        assertThat(task.getAttempts()).isEqualTo(3);
        assertThat(task.getLastError()).isEqualTo("recovered after stall");
    }
}
