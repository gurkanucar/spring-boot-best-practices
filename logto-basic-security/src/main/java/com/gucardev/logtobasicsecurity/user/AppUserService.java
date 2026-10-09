package com.gucardev.logtobasicsecurity.user;

import com.gucardev.logtobasicsecurity.security.AuthUser;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class AppUserService {

    private static final Set<String> THEMES = Set.of("light", "dark");

    private final AppUserRepository repository;

    /** On sign-in: creates the local user if needed, refreshes e-mail and name, records the sign-in. */
    @Transactional
    public AppUser recordSignIn(AuthUser user) {
        AppUser appUser = sync(user.getId(), user.getEmail(), user.getDisplayName());
        appUser.signedIn(Instant.now());
        return appUser;
    }

    /**
     * From a Logto webhook: the user was created or changed in Logto. Creates or updates, so the same
     * event delivered twice, or both {@code PostRegister} and {@code User.Created} for one sign-up,
     * leave one row.
     */
    @Transactional
    public AppUser syncFromLogto(String logtoId, String email, String name, String username) {
        return sync(logtoId, email, displayName(name, username, email));
    }

    /**
     * From a Logto webhook: the user was deleted in Logto. Deleting is fine here because nothing
     * references {@code app_user}; once other tables do (orders, invoices), anonymise the row
     * instead, so their history keeps a user to point to.
     */
    @Transactional
    public void deleteByLogtoId(String logtoId) {
        repository.findByLogtoId(logtoId).ifPresent(repository::delete);
    }

    /**
     * Saves what the user chose in this application. Not from Logto: not touched by sign-ins or webhooks,
     * which only refresh e-mail and name.
     */
    @Transactional
    public void updatePreferences(String logtoId, String nickname, String theme) {
        String cleanNickname = nickname == null || nickname.isBlank() ? null : nickname.strip();
        if (cleanNickname != null && cleanNickname.length() > 50) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nickname is longer than 50 characters");
        }
        if (theme != null && !THEMES.contains(theme)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Theme must be one of " + THEMES);
        }
        repository.findByLogtoId(logtoId)
                .orElseThrow(() -> new IllegalStateException("No app_user for " + logtoId))
                .updatePreferences(cleanNickname, theme);
    }

    @Transactional(readOnly = true)
    public Optional<AppUser> findByLogtoId(String logtoId) {
        return repository.findByLogtoId(logtoId);
    }

    private AppUser sync(String logtoId, String email, String name) {
        repository.insertIfMissing(logtoId, Instant.now());
        AppUser appUser = repository.findByLogtoId(logtoId).orElseThrow();
        appUser.updateProfile(email, name);
        return appUser;
    }

    /** {@code name} is empty unless set in Logto; Logto's {@code username} is the usual fallback. */
    private static String displayName(String name, String username, String email) {
        if (name != null) {
            return name;
        }
        return username != null ? username : email;
    }
}
