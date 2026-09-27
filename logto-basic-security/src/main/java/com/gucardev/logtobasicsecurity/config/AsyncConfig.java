package com.gucardev.logtobasicsecurity.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.security.concurrent.DelegatingSecurityContextRunnable;

/**
 * {@code @Async} methods run as the user who called them.
 *
 * <p>Spring Security keeps the signed-in user in a thread-local, so on an executor's thread it is
 * normally empty. Spring Boot applies a {@link TaskDecorator} bean to its own executor (the one
 * {@code @Async} uses); this one runs when a task is submitted, on the request's thread: it captures
 * the security context there, sets it on the executor's thread for the task and clears it after.
 * Threads are reused, so clearing matters: never let one user's context stay on a pooled thread.
 *
 * <p>The context is a snapshot: a long task keeps the user's roles as they were when it started.
 * Jobs without a user (scheduled, from a queue) do not have one to carry; pass the user id instead.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean
    TaskDecorator securityContextTaskDecorator() {
        return DelegatingSecurityContextRunnable::new;
    }
}
