package com.gucardev.logtobasicsecurity.webhook;

import com.gucardev.logtobasicsecurity.user.AppUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Keeps {@code app_user} in step with Logto. The events to subscribe to in the Logto console:
 * <ul>
 *   <li>{@code PostRegister}: a user signed up on Logto's sign-in page</li>
 *   <li>{@code User.Created}: a user was created, by signing up <em>or</em> by an admin in the console
 *       or the Management API; without it, users created by an admin appear here only after their
 *       first sign-in</li>
 *   <li>{@code User.Data.Updated}: e-mail, name, username changed in Logto</li>
 *   <li>{@code User.Deleted}</li>
 * </ul>
 * Every handler can run twice for the same change (Logto retries, and a sign-up sends both
 * {@code PostRegister} and {@code User.Created}), so each one leaves the same result when repeated.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LogtoUserSync {

    private final AppUserService appUserService;

    public void handle(LogtoWebhookEvent event) {
        switch (event.event()) {
            case "PostRegister" -> upsert(event.user());
            case "User.Created", "User.Data.Updated" -> upsert(event.data());
            case "User.Deleted" -> delete(event);
            // Other events the webhook may be subscribed to (PostSignIn, Role.*, ...): nothing to do.
            default -> log.debug("Logto webhook {} ignored", event.event());
        }
    }

    private void upsert(LogtoWebhookEvent.User user) {
        if (user == null || user.id() == null) {
            throw new IllegalArgumentException("Logto webhook without a user");
        }
        appUserService.syncFromLogto(user.id(), user.primaryEmail(), user.name(), user.username());
        log.info("Logto user {} synced", user.id());
    }

    private void delete(LogtoWebhookEvent event) {
        Object userId = event.params() == null ? null : event.params().get("userId");
        if (userId == null) {
            throw new IllegalArgumentException("User.Deleted without params.userId");
        }
        appUserService.deleteByLogtoId(userId.toString());
        log.info("Logto user {} deleted", userId);
    }
}
