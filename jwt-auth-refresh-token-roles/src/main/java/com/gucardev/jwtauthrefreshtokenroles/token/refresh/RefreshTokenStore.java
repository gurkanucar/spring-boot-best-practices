package com.gucardev.jwtauthrefreshtokenroles.token.refresh;

import java.time.Duration;
import java.util.Optional;

public interface RefreshTokenStore {

    void save(String token, String userId, Duration ttl);

    /** Deletes the token if present and returns its userId (rotation: single use). */
    Optional<String> consume(String token);

    /**
     * Drops every refresh token belonging to the user. Called after a password change or reset so
     * sessions on other devices cannot survive with an old token.
     */
    void revokeAllForUser(String userId);
}
