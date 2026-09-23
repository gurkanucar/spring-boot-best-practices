package com.gucardev.quartzschedulershedlock.task;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The entire point of this project: two concurrent callers of the same
 * {@code @SchedulerLock}-protected method must not both run the method body — this is
 * what stands in for "two application instances firing the same trigger at once".
 *
 * <p>{@code scheduling.enabled=false} keeps the real {@code @Scheduled} cron from also
 * firing mid-test and racing these two threads for the same lock.
 */
@SpringBootTest
@TestPropertySource(properties = "scheduling.enabled=false")
class SchedulerLockConcurrencyTest {

    @Autowired
    private EveryMinuteTask everyMinuteTask;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * H2 in-memory databases are keyed by name within a JVM and outlive any single
     * Spring context, so the "everyMinuteTask" lock row can still be held here by an
     * unrelated real firing from another test class's context, whose lockAtLeastFor
     * keeps it "locked" for 10s after that job already returned. This test is
     * specifically about lock contention between ITS OWN two threads, so it clears any
     * pre-existing lock row first to control the precondition it's testing.
     */
    @BeforeEach
    void resetState() {
        everyMinuteTask.resetExecutionCount();
        jdbcTemplate.update("DELETE FROM shedlock WHERE name = ?", "everyMinuteTask");
    }

    @Test
    void onlyOneConcurrentCallActuallyExecutesTheLockedMethod() throws Exception {
        CyclicBarrier barrier = new CyclicBarrier(2);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> attempt(barrier));
            var second = executor.submit(() -> attempt(barrier));
            first.get(10, TimeUnit.SECONDS);
            second.get(10, TimeUnit.SECONDS);
        }

        assertThat(everyMinuteTask.executionCount()).isEqualTo(1);
    }

    private Void attempt(CyclicBarrier barrier) throws Exception {
        barrier.await(5, TimeUnit.SECONDS);
        try {
            everyMinuteTask.run();
        } catch (IllegalStateException simulatedFailure) {
            // EveryMinuteTask randomly fails ~30% of the time by design; this test only
            // cares whether the method body ran once or twice, not which branch it took.
        }
        return null;
    }
}
