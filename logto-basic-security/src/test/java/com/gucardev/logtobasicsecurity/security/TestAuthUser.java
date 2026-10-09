package com.gucardev.logtobasicsecurity.security;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;

/** Signed-in users for tests, as LogtoOidcUserService would build them. */
public final class TestAuthUser {

    private TestAuthUser() {
    }

    public static AuthUser of(String id, String... roles) {
        return of(Map.of("sub", id), roles);
    }

    public static AuthUser of(Map<String, Object> claims, String... roles) {
        var idToken = new OidcIdToken("token", Instant.now(), Instant.now().plusSeconds(60), claims);
        return new AuthUser(new DefaultOidcUser(List.of(), idToken), List.of(roles));
    }
}
