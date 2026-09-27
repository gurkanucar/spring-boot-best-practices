package com.gucardev.logtobasicsecurity.security;

import com.gucardev.logtobasicsecurity.user.AppUserService;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Component;

/**
 * Runs once per sign-in, after Logto has answered: keeps a local copy of the user and turns Logto
 * roles into Spring Security authorities.
 *
 * <p>Every role Logto sends is mapped, whatever its name: there is no list of roles in the code.
 * A role created in the Logto console and assigned to a user works from that user's next sign-in;
 * the code only names a role where it checks it ({@code hasRole("admin")}).
 */
@Component
@RequiredArgsConstructor
public class LogtoOidcUserService extends OidcUserService {

    /** Role names, requested with the {@code roles} scope; e.g. {@code ["admin"]}. */
    static final String ROLES_CLAIM = "roles";

    private final AppUserService appUserService;

    @Override
    public OidcUser loadUser(OidcUserRequest userRequest) {
        OidcUser user = super.loadUser(userRequest); // ID token claims + the userinfo endpoint
        appUserService.recordSignIn(user);

        Set<GrantedAuthority> authorities = new HashSet<>(user.getAuthorities()); // OIDC_USER, SCOPE_*
        authorities.addAll(rolesOf(user));
        return new DefaultOidcUser(authorities, user.getIdToken(), user.getUserInfo(), IdTokenClaimNames.SUB);
    }

    /** Logto role {@code admin} becomes {@code ROLE_admin}, so {@code hasRole("admin")} works. */
    static List<GrantedAuthority> rolesOf(OidcUser user) {
        List<String> roles = user.getClaimAsStringList(ROLES_CLAIM);
        if (roles == null) {
            return List.of();
        }
        return roles.stream().<GrantedAuthority>map(role -> new SimpleGrantedAuthority("ROLE_" + role)).toList();
    }
}
