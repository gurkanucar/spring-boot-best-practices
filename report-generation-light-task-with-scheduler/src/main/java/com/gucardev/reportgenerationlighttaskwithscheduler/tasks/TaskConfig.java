package com.gucardev.reportgenerationlighttaskwithscheduler.tasks;

import javax.sql.DataSource;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableSchedulerLock(defaultLockAtMostFor = "PT10M")
public class TaskConfig {

    @Bean
    LockProvider lockProvider(DataSource dataSource) {
        return new JdbcTemplateLockProvider(JdbcTemplateLockProvider.Configuration.builder()
                .withJdbcTemplate(new JdbcTemplate(dataSource))
                .usingDbTime()
                .build());
    }

    /**
     * Fixed size, no queue: a submitted task starts right away or is rejected. The poller only
     * claims as many tasks as there are free workers, so claimed tasks never wait in memory
     * (where a crash would strand them as RUNNING).
     */
    @Bean
    ThreadPoolTaskExecutor taskWorkerExecutor(TaskProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.workers());
        executor.setMaxPoolSize(properties.workers());
        executor.setQueueCapacity(0);
        executor.setThreadNamePrefix("task-worker-");
        // TaskPoller.stop() already waited for running tasks; whatever is left is interrupted and
        // later rescheduled by stuck-task recovery.
        executor.setWaitForTasksToCompleteOnShutdown(false);
        return executor;
    }
}
