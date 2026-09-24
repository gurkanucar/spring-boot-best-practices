package com.gucardev.restapiintegration.remote;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import org.springframework.http.HttpHeaders;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Protects the simulated remote product API the way many partner APIs do: HTTP Basic
 * authentication plus an API key header. 401 without valid credentials, 403 without the key.
 */
public class RemoteAuthFilter extends OncePerRequestFilter {

    private final MockRemoteProperties properties;

    public RemoteAuthFilter(MockRemoteProperties properties) {
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String expected = "Basic " + Base64.getEncoder().encodeToString(
                (properties.username() + ":" + properties.password()).getBytes(StandardCharsets.UTF_8));
        if (!constantTimeEquals(expected, request.getHeader(HttpHeaders.AUTHORIZATION))) {
            response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Basic realm=\"remote-api\"");
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }
        if (!constantTimeEquals(properties.apiKey(), request.getHeader("X-Api-Key"))) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }
        chain.doFilter(request, response);
    }

    private static boolean constantTimeEquals(String expected, String actual) {
        return actual != null && MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8), actual.getBytes(StandardCharsets.UTF_8));
    }
}
