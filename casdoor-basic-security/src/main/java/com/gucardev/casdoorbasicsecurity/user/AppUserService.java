package com.gucardev.casdoorbasicsecurity.user;

import com.gucardev.casdoorbasicsecurity.security.AuthUser;
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
        repository.insertIfMissing(user.getId(), Instant.now());
        AppUser appUser = repository.findByCasdoorId(user.getId()).orElseThrow();
        appUser.updateProfile(user.getEmail(), user.getDisplayName());
        appUser.signedIn(Instant.now());
        return appUser;
    }

    /**
     * Saves what the user chose in this application. Not from Casdoor: not touched by sign-ins or webhooks,
     * which only refresh e-mail and name.
     */
    @Transactional
    public void updatePreferences(String casdoorId, String nickname, String theme) {
        String cleanNickname = nickname == null || nickname.isBlank() ? null : nickname.strip();
        if (cleanNickname != null && cleanNickname.length() > 50) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nickname is longer than 50 characters");
        }
        if (theme != null && !THEMES.contains(theme)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Theme must be one of " + THEMES);
        }
        repository.findByCasdoorId(casdoorId)
                .orElseThrow(() -> new IllegalStateException("No app_user for " + casdoorId))
                .updatePreferences(cleanNickname, theme);
    }

    @Transactional(readOnly = true)
    public Optional<AppUser> findByCasdoorId(String casdoorId) {
        return repository.findByCasdoorId(casdoorId);
    }
}
