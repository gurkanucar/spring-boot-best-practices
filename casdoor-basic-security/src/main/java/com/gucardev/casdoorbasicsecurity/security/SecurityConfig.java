package com.gucardev.casdoorbasicsecurity.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.client.oidc.web.logout.OidcClientInitiatedLogoutSuccessHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
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
     * Sign-in: an unauthenticated request to a protected page is redirected to Casdoor (there is only
     * one provider, so Spring skips its own login page), and back to {@code /login/oauth2/code/casdoor}.
     *
     * <p>Sign-out: {@code POST /logout} ends the session here AND at Casdoor (RP-initiated logout),
     * otherwise the next "sign in" would log the user in again without asking for a password.
     */
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, CasdoorOidcUserService oidcUserService,
                                            ClientRegistrationRepository clientRegistrations) throws Exception {
        var logoutAtCasdoor = new OidcClientInitiatedLogoutSuccessHandler(clientRegistrations);
        // Casdoor only redirects to URIs listed in the application's redirect URLs (see init_data.json).
        logoutAtCasdoor.setPostLogoutRedirectUri("{baseUrl}/");

        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/", "/error").permitAll()
                        // A role assigned to the user in the Casdoor console (Roles -> Users).
                        .requestMatchers("/admin/**").hasRole("admin")
                        .anyRequest().authenticated())
                .oauth2Login(login -> login.userInfoEndpoint(userInfo -> userInfo.oidcUserService(oidcUserService)))
                .logout(logout -> logout.logoutSuccessHandler(logoutAtCasdoor));
        return http.build();
    }

    /**
     * {@code admin} includes {@code user}: an admin passes every {@code hasRole("user")} check without
     * also being given the {@code user} role in Casdoor. Used by the URL rules and {@code @PreAuthorize}
     * alike. It applies only to checks: the user's authorities (see {@code /me}) stay as Casdoor sent them.
     */
    @Bean
    static RoleHierarchy roleHierarchy() {
        return RoleHierarchyImpl.withDefaultRolePrefix()
                .role("admin").implies("user")
                .build();
    }
}
