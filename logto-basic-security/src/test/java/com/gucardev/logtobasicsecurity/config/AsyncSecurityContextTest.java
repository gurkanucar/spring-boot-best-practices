package com.gucardev.logtobasicsecurity.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.gucardev.logtobasicsecurity.report.ReportService;
import com.gucardev.logtobasicsecurity.security.CurrentUserService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.task.TaskExecutionAutoConfiguration;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
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
        signIn("logto-user-1");

        Map<String, Object> result = reportService.generate().get();

        assertThat(result.get("thread")).isNotEqualTo(Thread.currentThread().getName());
        assertThat(result.get("requestedBy")).isEqualTo("logto-user-1");
    }

    @Test
    void eachTaskGetsItsOwnCallersUserEvenOnReusedThreads() throws Exception {
        // The pool reuses its threads: a context left behind by one task must not leak into the next.
        signIn("logto-user-1");
        assertThat(reportService.generate().get().get("requestedBy")).isEqualTo("logto-user-1");

        signIn("logto-user-2");
        assertThat(reportService.generate().get().get("requestedBy")).isEqualTo("logto-user-2");
    }

    private static void signIn(String logtoId) {
        var idToken = new OidcIdToken("token", Instant.now(), Instant.now().plusSeconds(60), Map.of("sub", logtoId));
        var user = new DefaultOidcUser(List.of(), idToken);
        SecurityContextHolder.getContext().setAuthentication(new OAuth2AuthenticationToken(user, List.of(), "logto"));
    }
}
