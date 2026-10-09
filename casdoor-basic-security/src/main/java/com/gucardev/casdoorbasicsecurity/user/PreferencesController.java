package com.gucardev.casdoorbasicsecurity.user;

import com.gucardev.casdoorbasicsecurity.security.AuthUser;
import com.gucardev.casdoorbasicsecurity.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Data that belongs to this application, stored on the user's {@code app_user} row. A browser session,
 * so {@code PUT} needs the CSRF token like any other form post. {@code GET /me} shows the result.
 */
@RestController
@RequiredArgsConstructor
public class PreferencesController {

    private final AppUserService appUserService;

    public record Preferences(String nickname, String theme) {
    }

    @PutMapping("/me/preferences")
    public ResponseEntity<Void> update(@CurrentUser AuthUser user, @RequestBody Preferences preferences) {
        appUserService.updatePreferences(user.getId(), preferences.nickname(), preferences.theme());
        return ResponseEntity.noContent().build();
    }
}
