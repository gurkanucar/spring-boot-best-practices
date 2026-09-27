package com.gucardev.logtobasicsecurity.security;

import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Component;

/**
 * The signed-in user anywhere in the code, without passing it down from the controller.
 *
 * <p>Reads Spring Security's context, which belongs to the thread handling the request: it is empty
 * in {@code @Async} methods, scheduled jobs and threads you start yourself. Pass the user id as a
 * parameter there.
 *
 * <p>What it returns is what Logto sent at sign-in, kept in the session: changes made in Logto
 * later (e-mail, roles) show up after the next sign-in.
 */
@Component
public class CurrentUserService {

    /** Empty when nobody is signed in. */
    public Optional<OidcUser> find() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof OidcUser user) {
            return Optional.of(user);
        }
        return Optional.empty();
    }

    /** @throws IllegalStateException when nobody is signed in: a bug where only signed-in users get */
    public OidcUser get() {
        return find().orElseThrow(() -> new IllegalStateException("No signed-in user"));
    }

    /** Logto's user id ({@code sub}): stable, unlike the e-mail. The key to store with the user's data. */
    public String logtoId() {
        return get().getSubject();
    }
}
