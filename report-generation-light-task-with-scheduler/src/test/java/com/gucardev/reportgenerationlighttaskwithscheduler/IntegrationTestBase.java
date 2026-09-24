package com.gucardev.reportgenerationlighttaskwithscheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.gucardev.reportgenerationlighttaskwithscheduler.tasks.entity.BackgroundTask;
import com.gucardev.reportgenerationlighttaskwithscheduler.tasks.entity.TaskStatus;
import com.gucardev.reportgenerationlighttaskwithscheduler.tasks.handler.ReportGenerationHandler;
import com.gucardev.reportgenerationlighttaskwithscheduler.tasks.repository.BackgroundTaskRepository;
import com.gucardev.reportgenerationlighttaskwithscheduler.tasks.service.TaskService;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * One application context shared by all tests. The report handler is a spy: real by default,
 * stubbed where a test needs a failing or slow handler.
 */
@SpringBootTest(properties = {
        "tasks.poll-interval=100ms",
        "tasks.concurrency.REPORT_GENERATION=2"
})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
public abstract class IntegrationTestBase {

    protected static final Duration TIMEOUT = Duration.ofSeconds(20);

    @Autowired
    protected MockMvc mockMvc;
    @Autowired
    protected TaskService taskService;
    @Autowired
    protected BackgroundTaskRepository tasks;
    @Autowired
    protected JdbcClient jdbc;
    @MockitoSpyBean
    protected ReportGenerationHandler reportHandler;

    @AfterEach
    void removeTasksOfThisTest() {
        // Workers may still be finishing; delete only once nothing runs.
        await().atMost(TIMEOUT).until(() -> tasks.findAll().stream().noneMatch(t -> t.getStatus() == TaskStatus.RUNNING));
        jdbc.sql("delete from background_task").update();
        jdbc.sql("delete from report").update();
        jdbc.sql("delete from report_request").update();
    }

    protected BackgroundTask task(UUID id) {
        return tasks.findById(id).orElseThrow();
    }

    protected BackgroundTask awaitStatus(UUID id, TaskStatus status) {
        await().atMost(TIMEOUT).untilAsserted(() -> assertThat(task(id).getStatus()).isEqualTo(status));
        return task(id);
    }
}
