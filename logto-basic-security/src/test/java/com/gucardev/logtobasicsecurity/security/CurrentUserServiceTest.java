package com.gucardev.logtobasicsecurity.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;

class CurrentUserServiceTest {

    private final CurrentUserService currentUser = new CurrentUserService();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void returnsTheSignedInUser() {
        var user = TestAuthUser.of(Map.of("sub", "logto-user-1", "email", "alice@example.com"), "admin");
        // What Spring Security puts into the context after an OIDC sign-in.
        SecurityContextHolder.getContext().setAuthentication(new OAuth2AuthenticationToken(user, List.of(), "logto"));

        assertThat(currentUser.id()).isEqualTo("logto-user-1");
        assertThat(currentUser.get().getEmail()).isEqualTo("alice@example.com");
        assertThat(currentUser.get().getRoles()).containsExactly("admin");
    }

    @Test
    void nobodySignedIn() {
        assertThat(currentUser.find()).isEmpty();
        assertThatThrownBy(currentUser::get).isInstanceOf(IllegalStateException.class);
    }
}
