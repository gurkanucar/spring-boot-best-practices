package com.gucardev.logtobasicsecurity.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

class LogtoOidcUserServiceTest {

    @Test
    void logtoRolesBecomeRoleAuthorities() {
        OidcUser user = userWith(Map.of("sub", "logto-user-1", "roles", List.of("admin", "editor")));

        assertThat(LogtoOidcUserService.rolesOf(user)).extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_admin", "ROLE_editor");
    }

    @Test
    void noRolesClaimMeansNoRoles() {
        assertThat(LogtoOidcUserService.rolesOf(userWith(Map.of("sub", "logto-user-1")))).isEmpty();
    }

    private static OidcUser userWith(Map<String, Object> claims) {
        var idToken = new OidcIdToken("token", Instant.now(), Instant.now().plusSeconds(60), claims);
        return new DefaultOidcUser(List.of(), idToken);
    }
}
