package com.gucardev.jwtauthrefreshtokenroles.token.access;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class CurrentUserTest {

    @Test
    void readsIdentityAndClaimsFromJwt() {
        UUID id = UUID.randomUUID();
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject(id.toString())
                .claim("email", "jane@example.com")
                .claim("roles", List.of("USER"))
                .claim("tenant_id", "acme")
                .build();

        CurrentUser user = CurrentUser.from(jwt);

        assertThat(user.id()).isEqualTo(id);
        assertThat(user.email()).isEqualTo("jane@example.com");
        assertThat(user.roles()).containsExactly("USER");
        assertThat(user.claim("tenant_id")).contains("acme");
        assertThat(user.claim("missing")).isEmpty();
    }

    @Test
    void missingRolesClaimMeansNoRoles() {
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "RS256")
                .subject(UUID.randomUUID().toString()).claim("email", "a@b.c").build();

        assertThat(CurrentUser.from(jwt).roles()).isEmpty();
    }
}
