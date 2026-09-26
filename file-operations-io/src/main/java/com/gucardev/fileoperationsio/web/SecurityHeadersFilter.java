package com.gucardev.fileoperationsio.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * OWASP-recommended response headers for the API. Even if a stored file is opened in a browser,
 * it is not type-sniffed, cannot run scripts, cannot be framed and is not cached.
 * (With Spring Security, its default headers cover most of these.)
 */
@Component
public class SecurityHeadersFilter extends OncePerRequestFilter {

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        // Use the declared Content-Type as is; never guess (e.g. treat a .txt as HTML).
        response.setHeader("X-Content-Type-Options", "nosniff");
        // Load nothing, run no scripts, allow no framing.
        response.setHeader("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'; sandbox");
        // Clickjacking protection for browsers without CSP frame-ancestors.
        response.setHeader("X-Frame-Options", "DENY");
        // File URLs are not leaked to other sites.
        response.setHeader("Referrer-Policy", "no-referrer");
        // Other sites cannot embed these responses.
        response.setHeader("Cross-Origin-Resource-Policy", "same-origin");
        // Files and metadata are not kept by browsers or shared proxies.
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        chain.doFilter(request, response);
    }
}
