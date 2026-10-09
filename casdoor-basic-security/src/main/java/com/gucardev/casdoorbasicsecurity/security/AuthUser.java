package com.gucardev.casdoorbasicsecurity.security;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

/**
 * The signed-in user, already parsed: this is the principal in the session, so controllers take it
 * as {@code @CurrentUser AuthUser user} and services get it from {@link CurrentUserService}.
 * Nobody outside {@link CasdoorOidcUserService} needs to know how Casdoor spells things.
 *
 * <p>It is still an {@link OidcUser} (everything Casdoor sent is in {@link #getClaims()}), plus:
 * {@link #getId()}, {@link #getDisplayName()} and {@link #getRoles()} (role names as Casdoor has them,
 * e.g. {@code admin}; the matching authority is {@code ROLE_admin}).
 */
public class AuthUser extends DefaultOidcUser {

    private final List<String> roles;

    /** @param base what Spring built from the ID token and userinfo; @param roles role names, already parsed */
    public AuthUser(OidcUser base, List<String> roles) {
        super(authorities(base, roles), base.getIdToken(), base.getUserInfo());
        this.roles = List.copyOf(roles);
    }

    /** {@code sub}: stable, unlike the e-mail. The key to store with the user's data. */
    public String getId() {
        return getSubject();
    }

    /** Full name, else the login name, else the e-mail. */
    public String getDisplayName() {
        for (String candidate : new String[] {getFullName(), getPreferredUsername(), getClaimAsString("username"), getEmail()}) {
            if (candidate != null && !candidate.isBlank()) {
                return candidate;
            }
        }
        return null;
    }

    public List<String> getRoles() {
        return roles;
    }

    public boolean hasRole(String role) {
        return roles.contains(role);
    }

    private static Set<GrantedAuthority> authorities(OidcUser base, List<String> roles) {
        List<GrantedAuthority> all = new ArrayList<>(base.getAuthorities()); // OIDC_USER, SCOPE_*
        roles.forEach(role -> all.add(new SimpleGrantedAuthority("ROLE_" + role)));
        return all.stream().collect(Collectors.toUnmodifiableSet());
    }
}
