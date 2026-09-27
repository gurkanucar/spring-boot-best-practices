package com.gucardev.logtobasicsecurity.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;

class CurrentUserServiceTest {

    private final CurrentUserService currentUser = new CurrentUserService();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void returnsTheSignedInUser() {
        var idToken = new OidcIdToken("token", Instant.now(), Instant.now().plusSeconds(60),
                Map.of("sub", "logto-user-1", "email", "alice@example.com"));
        var user = new DefaultOidcUser(List.of(), idToken);
        // What Spring Security puts into the context after an OIDC sign-in.
        SecurityContextHolder.getContext().setAuthentication(new OAuth2AuthenticationToken(user, List.of(), "logto"));

        assertThat(currentUser.logtoId()).isEqualTo("logto-user-1");
        assertThat(currentUser.get().getEmail()).isEqualTo("alice@example.com");
    }

    @Test
    void nobodySignedIn() {
        assertThat(currentUser.find()).isEmpty();
        assertThatThrownBy(currentUser::get).isInstanceOf(IllegalStateException.class);
    }
}
