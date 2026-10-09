package com.gucardev.casdoorbasicsecurity.user;

import java.time.Instant;

/** The application's own record of a signed-in user, as shown by {@code /me}. */
public record LocalUser(Long id, String name, String nickname, String theme, Instant createdAt, Instant lastSignInAt) {

    public static LocalUser of(AppUser user) {
        return new LocalUser(user.getId(), user.getName(), user.getNickname(), user.getTheme(),
                user.getCreatedAt(), user.getLastSignInAt());
    }
}
