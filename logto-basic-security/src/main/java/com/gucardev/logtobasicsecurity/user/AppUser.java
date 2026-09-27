package com.gucardev.logtobasicsecurity.user;

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
 * The application's own record of a Logto user. Logto owns identity (password, e-mail verification,
 * social sign-in); this table is where the application attaches its own data to a user, linked by
 * Logto's user id ({@code sub}), never by e-mail, which the user can change.
 *
 * <p>Kept in sync two ways: on every sign-in ({@code LogtoOidcUserService}) and by Logto webhooks
 * ({@code LogtoWebhookController}), which also cover users who never sign in here and changes made
 * in Logto between two sign-ins.
 */
@Entity
@Table(name = "app_user")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Logto's {@code sub} claim, {@code id} in webhook payloads. */
    @Column(nullable = false, unique = true, length = 64)
    private String logtoId;

    private String email;

    private String name;

    @Column(nullable = false)
    private Instant createdAt;

    /** Null until the user signs in to this application (a user created in Logto by a webhook). */
    private Instant lastSignInAt;

    // No constructor: rows are created by AppUserRepository.insertIfMissing, safe under concurrency.

    /** E-mail and name are copies of Logto's, refreshed on every sign-in and webhook. */
    void updateProfile(String email, String name) {
        this.email = email;
        this.name = name;
    }

    void signedIn(Instant now) {
        this.lastSignInAt = now;
    }
}
