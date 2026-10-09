package com.gucardev.logtobasicsecurity.security;

import com.gucardev.logtobasicsecurity.user.AppUserService;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
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
        OidcUser base = super.loadUser(userRequest); // ID token claims + the userinfo endpoint
        AuthUser user = new AuthUser(base, rolesOf(base.getClaims()));
        appUserService.recordSignIn(user);
        return user;
    }

    /** Role names from the {@code roles} claim, e.g. {@code ["admin"]}; none when the claim is missing. */
    static List<String> rolesOf(Map<String, Object> claims) {
        if (claims.get(ROLES_CLAIM) instanceof List<?> roles) {
            return roles.stream().filter(String.class::isInstance).map(String.class::cast).toList();
        }
        return List.of();
    }
}
