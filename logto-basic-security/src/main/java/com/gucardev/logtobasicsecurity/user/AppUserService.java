package com.gucardev.logtobasicsecurity.user;

import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AppUserService {

    private final AppUserRepository repository;

    /** On sign-in: creates the local user if needed, refreshes e-mail and name, records the sign-in. */
    @Transactional
    public AppUser recordSignIn(OidcUser user) {
        AppUser appUser = sync(user.getSubject(), user.getEmail(),
                displayName(user.getFullName(), user.getClaimAsString("username"), user.getEmail()));
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
