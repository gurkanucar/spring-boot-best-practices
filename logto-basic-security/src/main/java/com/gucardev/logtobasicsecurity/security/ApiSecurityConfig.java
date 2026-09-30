package com.gucardev.logtobasicsecurity.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * The API part: {@code /api/**} for clients that are not a browser session (Flutter, a SPA, another
 * service). They get an access token from Logto themselves and send it as {@code Authorization: Bearer}.
 * The rest of the application keeps its session-based Logto sign-in ({@link SecurityConfig}).
 *
 * <p>Access tokens for an API resource carry the granted <b>permissions</b> in {@code scope}, not
 * role names; Spring turns them into {@code SCOPE_*} authorities. In Logto a role grants permissions,
 * so "the user role may read reports" is configured there, and this class only asks for the permission.
 */
@Configuration
public class ApiSecurityConfig {

    /** Checked before the session chain (which has no order): it owns every /api/** request. */
    @Bean
    @Order(1)
    SecurityFilterChain apiSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/api/**")
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/api/reports").hasAuthority("SCOPE_read:reports")
                        .requestMatchers(HttpMethod.POST, "/api/reports").hasAuthority("SCOPE_write:reports")
                        .anyRequest().authenticated())
                // No token: 401 with WWW-Authenticate, never a redirect to a sign-in page.
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
                // No session and no cookie, so no CSRF exposure: every request carries its own token.
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(AbstractHttpConfigurer::disable);
        return http.build();
    }

    /**
     * Keys and algorithm (ES384) come from Logto's discovery document and JWKS on the first token, not
     * at startup. Only used by the API chain: the browser sign-in validates ID tokens separately.
     */
    @Bean
    JwtDecoder apiJwtDecoder(@Value("${app.api.issuer}") String issuer,
                             @Value("${app.api.audience}") String audience) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withIssuerLocation(issuer).build();
        decoder.setJwtValidator(apiTokenValidator(issuer, audience));
        return decoder;
    }

    /**
     * An RFC 9068 access token, which is what Logto issues for an API resource: header
     * {@code typ: at+jwt} (Spring's default validator only accepts {@code JWT} and would reject every
     * Logto access token), the required claims ({@code exp}, {@code iat}, {@code sub}, {@code jti},
     * {@code client_id}), this issuer, and this API as the audience. Without the audience check any
     * valid token from this Logto would be accepted, e.g. one issued for the Management API; without
     * the type check an ID token could be replayed as an access token.
     */
    static OAuth2TokenValidator<Jwt> apiTokenValidator(String issuer, String audience) {
        return JwtValidators.createAtJwtValidator()
                .issuer(issuer)
                .audience(audience)
                .build();
    }
}
