package com.gucardev.logtobasicsecurity.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LogtoOidcUserServiceTest {

    @Test
    void roleNamesAreTakenFromTheRolesClaim() {
        assertThat(LogtoOidcUserService.rolesOf(Map.of("roles", List.of("admin", "editor"))))
                .containsExactly("admin", "editor");
    }

    @Test
    void noRolesClaimMeansNoRoles() {
        assertThat(LogtoOidcUserService.rolesOf(Map.of("sub", "logto-user-1"))).isEmpty();
    }

    @Test
    void rolesBecomeRoleAuthoritiesOfTheAuthUser() {
        var user = TestAuthUser.of("logto-user-1", "admin", "editor");

        assertThat(user.getRoles()).containsExactly("admin", "editor");
        assertThat(user.getAuthorities()).extracting(Object::toString).contains("ROLE_admin", "ROLE_editor");
    }
}
