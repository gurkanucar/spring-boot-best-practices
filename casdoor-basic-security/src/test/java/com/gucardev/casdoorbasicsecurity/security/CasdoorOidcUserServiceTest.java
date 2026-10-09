package com.gucardev.casdoorbasicsecurity.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CasdoorOidcUserServiceTest {

    @Test
    void roleNamesBecomeRoleAuthorities() {
        var claims = Map.<String, Object>of("roles", List.of("admin", "editor"));

        assertThat(CasdoorOidcUserService.rolesOf(claims)).containsExactly("admin", "editor");
    }

    @Test
    void roleObjectsOfTheDefaultTokenFormatBecomeRoleAuthorities() {
        // Casdoor's default "JWT" token format: whole role objects, not names.
        var claims = Map.<String, Object>of("roles", List.of(
                Map.of("owner", "demo", "name", "admin", "isEnabled", true),
                Map.of("owner", "demo", "name", "user")));

        assertThat(CasdoorOidcUserService.rolesOf(claims)).containsExactly("admin", "user");
    }

    @Test
    void rolesBecomeRoleAuthoritiesOfTheAuthUser() {
        var user = TestAuthUser.of("casdoor-user-1", "admin", "editor");

        assertThat(user.getRoles()).containsExactly("admin", "editor");
        assertThat(user.getAuthorities()).extracting(Object::toString).contains("ROLE_admin", "ROLE_editor");
    }

    @Test
    void noRolesClaimMeansNoRoles() {
        assertThat(CasdoorOidcUserService.rolesOf(Map.of("sub", "casdoor-user-1"))).isEmpty();
        assertThat(CasdoorOidcUserService.rolesOf(Map.of("roles", "admin"))).isEmpty();
    }
}
