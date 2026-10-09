package com.gucardev.casdoorbasicsecurity.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.gucardev.casdoorbasicsecurity.report.ReportService;
import com.gucardev.casdoorbasicsecurity.security.CurrentUserService;
import com.gucardev.casdoorbasicsecurity.security.TestAuthUser;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.task.TaskExecutionAutoConfiguration;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

/**
 * {@code AsyncConfig} with Spring Boot's own executor, which applies the {@code TaskDecorator}: an
 * {@code @Async} method sees the caller's user although it runs on another thread.
 */
@SpringJUnitConfig({AsyncConfig.class, CurrentUserService.class, ReportService.class})
@ImportAutoConfiguration(TaskExecutionAutoConfiguration.class)
class AsyncSecurityContextTest {

    @Autowired
    ReportService reportService;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void asyncMethodRunsAsTheCallingUser() throws Exception {
        signIn("casdoor-user-1");

        Map<String, Object> result = reportService.generate().get();

        assertThat(result.get("thread")).isNotEqualTo(Thread.currentThread().getName());
        assertThat(result.get("requestedBy")).isEqualTo("casdoor-user-1");
    }

    @Test
    void eachTaskGetsItsOwnCallersUserEvenOnReusedThreads() throws Exception {
        // The pool reuses its threads: a context left behind by one task must not leak into the next.
        signIn("casdoor-user-1");
        assertThat(reportService.generate().get().get("requestedBy")).isEqualTo("casdoor-user-1");

        signIn("casdoor-user-2");
        assertThat(reportService.generate().get().get("requestedBy")).isEqualTo("casdoor-user-2");
    }

    private static void signIn(String casdoorId) {
        var user = TestAuthUser.of(casdoorId);
        SecurityContextHolder.getContext().setAuthentication(new OAuth2AuthenticationToken(user, List.of(), "casdoor"));
    }
}
