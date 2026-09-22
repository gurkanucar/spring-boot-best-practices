package com.gucardev.slf4jlogging.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Level;
import com.gucardev.slf4jlogging.support.LogCapture;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.HandlerMapping;

class RequestLoggingFilterTest {

    private final RequestLoggingFilter filter = new RequestLoggingFilter();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void installsRequestIdAndRestoresPriorContextWithoutLoggingQuerySecrets() throws Exception {
        MDC.put("requestId", "outer-context");
        var request = new MockHttpServletRequest("POST", "/private/path-secret");
        request.setQueryString("token=query-secret");
        request.addHeader("X-Request-ID", "request-42");
        var response = new MockHttpServletResponse();
        try (var capture = new LogCapture(RequestLoggingFilter.class, Level.INFO)) {
            filter.doFilter(request, response, (req, res) -> {
                assertThat(MDC.get("requestId")).isEqualTo("request-42");
                req.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/private/{id}");
            });
            var event = capture.events().getFirst();
            assertThat(event.getMDCPropertyMap()).containsEntry("requestId", "request-42");
            assertThat(event.getKeyValuePairs().toString()).contains("/private/{id}")
                    .doesNotContain("path-secret", "query-secret");
        }
        assertThat(response.getHeader("X-Request-ID")).isEqualTo("request-42");
        assertThat(MDC.get("requestId")).isEqualTo("outer-context");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "bad\r\nvalue", "contains spaces", "x" +
            "1234567890123456789012345678901234567890123456789012345678901234567890"})
    void missingOrUnsafeRequestIdsAreReplaced(String supplied) throws Exception {
        var request = new MockHttpServletRequest();
        if (supplied != null) {
            request.addHeader("X-Request-ID", supplied);
        }
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) -> {});
        assertThat(response.getHeader("X-Request-ID"))
                .matches("[a-f0-9]{8}(-[a-f0-9]{4}){3}-[a-f0-9]{12}");
        assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
    }

    @Test
    void unexpectedFilterChainFailureStillCleansMdcAndLogs500() {
        try (var capture = new LogCapture(RequestLoggingFilter.class, Level.INFO)) {
            assertThatThrownBy(() -> filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(),
                    (req, res) -> { throw new ServletException("simulated"); }))
                    .isInstanceOf(ServletException.class);
            assertThat(capture.events().getFirst().getKeyValuePairs())
                    .anySatisfy(pair -> {
                        assertThat(pair.key).isEqualTo("status");
                        assertThat(pair.value).isEqualTo(500);
                    });
            assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
        }
    }
}
