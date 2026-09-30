package com.gucardev.logtobasicsecurity.api;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * A small bearer-token API, protected by {@code ApiSecurityConfig}. Try it with {@code http/api.http}.
 * Nothing is stored: the point is who may call what.
 */
@RestController
@RequestMapping("/api")
public class ApiController {

    public record Report(String name, String createdBy, Instant createdAt) {
    }

    public record CreateReportRequest(String name) {
    }

    /**
     * Any valid token. {@code subject} is the user for a user's token; for a machine-to-machine token
     * (client credentials) it is the application, and {@code clientId} tells which one.
     */
    @GetMapping("/whoami")
    public Map<String, Object> whoami(@AuthenticationPrincipal Jwt jwt, Authentication authentication) {
        return Map.of(
                "subject", jwt.getSubject(),
                "clientId", String.valueOf(jwt.getClaims().getOrDefault("client_id", "")),
                "audience", jwt.getAudience() == null ? List.of() : jwt.getAudience(),
                "authorities", authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority).sorted().toList(),
                "expiresAt", String.valueOf(jwt.getExpiresAt()));
    }

    /** Needs the {@code read:reports} permission (see ApiSecurityConfig). */
    @GetMapping("/reports")
    public List<Report> reports() {
        return List.of(
                new Report("Monthly sales", "system", Instant.parse("2026-09-01T00:00:00Z")),
                new Report("Active users", "system", Instant.parse("2026-09-15T00:00:00Z")));
    }

    /** Needs the {@code write:reports} permission. */
    @PostMapping("/reports")
    @ResponseStatus(HttpStatus.CREATED)
    public Report create(@RequestBody CreateReportRequest request, @AuthenticationPrincipal Jwt jwt) {
        return new Report(request.name(), jwt.getSubject(), Instant.now());
    }
}
