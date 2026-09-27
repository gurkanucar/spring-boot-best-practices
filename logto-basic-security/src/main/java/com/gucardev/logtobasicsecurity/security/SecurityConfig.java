package com.gucardev.logtobasicsecurity.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.client.oidc.authentication.OidcIdTokenDecoderFactory;
import org.springframework.security.oauth2.client.oidc.web.logout.OidcClientInitiatedLogoutSuccessHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoderFactory;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Two places to check roles, both used in this demo:
 * <ul>
 *   <li>URL rules below: a whole area of the application, e.g. everything under {@code /admin}.</li>
 *   <li>{@code @PreAuthorize} on a method ({@code @EnableMethodSecurity}): next to the code it
 *       protects, also for service methods that are not reached through a URL.</li>
 * </ul>
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    /**
     * Sign-in: an unauthenticated request to a protected page is redirected to Logto (there is only
     * one provider, so Spring skips its own login page), and back to {@code /login/oauth2/code/logto}.
     *
     * <p>Sign-out: {@code POST /logout} ends the session here AND at Logto (RP-initiated logout),
     * otherwise the next "sign in" would log the user in again without asking for a password.
     */
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, LogtoOidcUserService oidcUserService,
                                            ClientRegistrationRepository clientRegistrations) throws Exception {
        var logoutAtLogto = new OidcClientInitiatedLogoutSuccessHandler(clientRegistrations);
        // Must be registered in Logto as a post sign-out redirect URI.
        logoutAtLogto.setPostLogoutRedirectUri("{baseUrl}/");

        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/", "/error").permitAll()
                        // Logto's webhooks: no session, authenticated by their signature instead.
                        .requestMatchers("/webhooks/**").permitAll()
                        // A role assigned to the user in the Logto console (User management -> Roles).
                        .requestMatchers("/admin/**").hasRole("admin")
                        .anyRequest().authenticated())
                .oauth2Login(login -> login.userInfoEndpoint(userInfo -> userInfo.oidcUserService(oidcUserService)))
                .logout(logout -> logout.logoutSuccessHandler(logoutAtLogto))
                // CSRF protects browser sessions; a server-to-server call from Logto has no session and
                // no CSRF token.
                .csrf(csrf -> csrf.ignoringRequestMatchers("/webhooks/**"));
        return http.build();
    }

    /**
     * {@code admin} includes {@code user}: an admin passes every {@code hasRole("user")} check without
     * also being given the {@code user} role in Logto. Used by the URL rules and {@code @PreAuthorize}
     * alike. It applies only to checks: the user's authorities (see {@code /me}) stay as Logto sent them.
     *
     * <p>Only for roles that really include each other. Roles that exist today but are not known
     * here, or are added in Logto later, are unaffected: they simply imply nothing.
     */
    @Bean
    static RoleHierarchy roleHierarchy() {
        return RoleHierarchyImpl.withDefaultRolePrefix()
                .role("admin").implies("user")
                .build();
    }

    /**
     * Logto signs ID tokens with ES384 (see {@code id_token_signing_alg_values_supported} in
     * {@code /oidc/.well-known/openid-configuration}), Spring Security expects RS256 unless told
     * otherwise. Without this, every sign-in fails with {@code invalid_id_token}.
     */
    @Bean
    JwtDecoderFactory<ClientRegistration> idTokenDecoderFactory() {
        var factory = new OidcIdTokenDecoderFactory();
        factory.setJwsAlgorithmResolver(registration -> SignatureAlgorithm.ES384);
        return factory;
    }
}
