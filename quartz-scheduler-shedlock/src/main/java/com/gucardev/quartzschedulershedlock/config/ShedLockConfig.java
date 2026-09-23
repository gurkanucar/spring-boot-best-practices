package com.gucardev.quartzschedulershedlock.config;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * {@code defaultLockAtMostFor} is a safety net only — it is the lock's maximum
 * lifetime if the node that acquired it dies before releasing it. Under normal
 * operation every {@code @SchedulerLock} releases the lock as soon as its method
 * returns; each task below sets its own, much tighter, {@code lockAtMostFor}.
 */
@Configuration
@EnableSchedulerLock(defaultLockAtMostFor = "PT5M")
public class ShedLockConfig {

    @Bean
    public LockProvider lockProvider(DataSource dataSource) {
        return new JdbcTemplateLockProvider(dataSource);
    }
}
