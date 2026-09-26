package com.gucardev.reportgenerationlighttaskwithscheduler;

import static org.awaitility.Awaitility.await;

import com.gucardev.reportgenerationlighttaskwithscheduler.report.ReportMailer;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** One application context for all tests: fast polling, 1s first retry, a mocked mailer. */
@SpringBootTest(properties = {"tasks.poll-interval=100ms", "tasks.first-retry-delay=1s"})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
public abstract class IntegrationTestBase {

    protected static final Duration TIMEOUT = Duration.ofSeconds(20);

    @Autowired
    protected MockMvc mockMvc;
    @Autowired
    protected JdbcClient jdbc;
    @MockitoBean
    protected ReportMailer mailer;

    @AfterEach
    void cleanUp() {
        await().atMost(TIMEOUT).until(() -> jdbc.sql("select count(*) from background_task where status = 'RUNNING'")
                .query(Long.class).single() == 0);
        jdbc.sql("delete from background_task").update();
        jdbc.sql("delete from report").update();
        jdbc.sql("delete from report_request").update();
    }
}
