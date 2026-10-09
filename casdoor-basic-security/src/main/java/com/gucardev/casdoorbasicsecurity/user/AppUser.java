package com.gucardev.casdoorbasicsecurity.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * The application's own record of a Casdoor user. Casdoor owns identity (password, e-mail verification,
 * social sign-in); this table is where the application attaches its own data to a user, linked by
 * Casdoor's user id ({@code sub}), never by e-mail, which the user can change.
 *
 * <p>Kept in sync on every sign-in ({@code CasdoorOidcUserService}).
 */
@Entity
@Table(name = "app_user")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Casdoor's {@code sub} claim: the user's UUID. */
    @Column(nullable = false, unique = true, length = 64)
    private String casdoorId;

    private String email;

    private String name;

    @Column(nullable = false)
    private Instant createdAt;

    /** Set at each sign-in. */
    private Instant lastSignInAt;

    /**
     * Chosen by the user in this application; Casdoor knows nothing about it. This is what a local user
     * table is for: data that is ours, next to the identity that is Casdoor's. Null until set.
     */
    @Column(length = 50)
    private String nickname;

    /** Interface preference, {{@code light}} or {{@code dark}}; also ours. Null until set. */
    @Column(length = 10)
    private String theme;

    // No constructor: rows are created by AppUserRepository.insertIfMissing, safe under concurrency.

    /** E-mail and name are copies of Casdoor's, refreshed on every sign-in. */
    void updateProfile(String email, String name) {
        this.email = email;
        this.name = name;
    }

    void updatePreferences(String nickname, String theme) {
        this.nickname = nickname;
        this.theme = theme;
    }

    void signedIn(Instant now) {
        this.lastSignInAt = now;
    }
}
