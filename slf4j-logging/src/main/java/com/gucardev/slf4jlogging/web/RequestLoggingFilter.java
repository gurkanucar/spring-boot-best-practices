package com.gucardev.slf4jlogging.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

/** HTTP metadata only: no bodies, credentials, raw URLs or query parameters. */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final Pattern SAFE_REQUEST_ID = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        var previousContext = MDC.getCopyOfContextMap();
        String supplied = request.getHeader("X-Request-ID");
        String requestId = supplied != null && SAFE_REQUEST_ID.matcher(supplied).matches()
                ? supplied : UUID.randomUUID().toString();
        MDC.put("requestId", requestId);
        response.setHeader("X-Request-ID", requestId);
        long started = System.nanoTime();
        boolean completed = false;
        try {
            filterChain.doFilter(request, response);
            completed = true;
        } finally {
            try {
                // Matched route templates avoid exposing arbitrary path segments and query secrets.
                Object route = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
                log.atInfo().addKeyValue("event", "http.completed")
                        .addKeyValue("method", request.getMethod())
                        .addKeyValue("route", route == null ? "unmatched" : route.toString())
                        .addKeyValue("status", completed ? response.getStatus() : 500)
                        .addKeyValue("elapsedMs", TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started))
                        .log("HTTP request completed");
            } finally {
                // Servlet threads are reused. Never leave one request's context on the next request.
                if (previousContext == null) {
                    MDC.clear();
                } else {
                    MDC.setContextMap(previousContext);
                }
            }
        }
    }
}
