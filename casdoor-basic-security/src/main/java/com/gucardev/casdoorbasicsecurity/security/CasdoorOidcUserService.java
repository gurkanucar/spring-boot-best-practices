package com.gucardev.casdoorbasicsecurity.security;

import com.gucardev.casdoorbasicsecurity.user.AppUserService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Component;

/**
 * Runs once per sign-in, after Casdoor has answered: keeps a local copy of the user and turns Casdoor
 * roles into Spring Security authorities.
 *
 * <p>Every role Casdoor sends is mapped, whatever its name: there is no list of roles in the code.
 * A role assigned to a user in the Casdoor console works from that user's next sign-in.
 */
@Component
@RequiredArgsConstructor
public class CasdoorOidcUserService extends OidcUserService {

    /** Casdoor puts the user's roles into the ID token under this claim. */
    static final String ROLES_CLAIM = "roles";

    private final AppUserService appUserService;

    @Override
    public OidcUser loadUser(OidcUserRequest userRequest) {
        OidcUser base = super.loadUser(userRequest); // ID token claims + the userinfo endpoint
        AuthUser user = new AuthUser(base, rolesOf(userRequest.getIdToken().getClaims()));
        appUserService.recordSignIn(user);
        return user;
    }

    /**
     * Role names from the {@code roles} claim; {@link AuthUser} turns {@code admin} into {@code ROLE_admin}.
     *
     * <p>In the default token format ({@code JWT}) the {@code roles} claim is a list of whole role
     * objects ({@code [{"owner": "demo", "name": "admin", ...}]}); a custom token format with an
     * array attribute gives plain names ({@code ["admin"]}). Both are accepted.
     */
    static List<String> rolesOf(Map<String, Object> claims) {
        if (!(claims.get(ROLES_CLAIM) instanceof List<?> roles)) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        for (Object role : roles) {
            Object name = role instanceof Map<?, ?> map ? map.get("name") : role;
            if (name instanceof String s && !s.isBlank()) {
                names.add(s);
            }
        }
        return names;
    }
}
