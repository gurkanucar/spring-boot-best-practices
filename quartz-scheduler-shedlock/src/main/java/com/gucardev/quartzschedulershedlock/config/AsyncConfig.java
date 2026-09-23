package com.gucardev.quartzschedulershedlock.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Fire-and-forget {@code @Async void} methods can't propagate exceptions back to any
 * caller — Spring routes them to {@link AsyncUncaughtExceptionHandler} instead. Spring's
 * own default handler ({@code SimpleAsyncUncaughtExceptionHandler}) already logs every
 * such failure at ERROR, so this custom one isn't here to prevent a silent failure that
 * wasn't going to happen anyway — it exists to name the failing task/method explicitly
 * in the log line, as the one place to add your own policy (metrics, alerting, a
 * project-specific format) for every async failure, not just this one.
 */
@Slf4j
@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {

    public static final String QUARTZ_ASYNC_EXECUTOR = "quartzAsyncExecutor";

    @Bean(QUARTZ_ASYNC_EXECUTOR)
    @Override
    public Executor getAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("async-task-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(5);
        return executor;
    }

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (Throwable ex, java.lang.reflect.Method method, Object... params) -> {
            String taskName = method.getDeclaringClass().getSimpleName();
            log.error("Uncaught exception in async method {}#{}", taskName, method.getName(), ex);
        };
    }
}
