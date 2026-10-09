package com.gucardev.casdoorbasicsecurity.home;

import com.gucardev.casdoorbasicsecurity.security.AuthService;
import com.gucardev.casdoorbasicsecurity.security.AuthUser;
import com.gucardev.casdoorbasicsecurity.security.CurrentUser;
import com.gucardev.casdoorbasicsecurity.user.UserProfile;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class HomeController {

    private final AuthService authService;

    /** Public. */
    @GetMapping("/")
    public Map<String, Object> home(@CurrentUser AuthUser user) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("signedIn", user != null);
        result.put("signIn", "/oauth2/authorization/casdoor");
        result.put("me", "/me");
        result.put("admin", "/admin (Casdoor role: admin)");
        result.put("user", "/user (Casdoor role: user)");
        result.put("reports", "/reports (Casdoor role: admin or user)");
        result.put("signOut", "/logout");
        return result;
    }

    /**
     * Signed-in users: who they are and the application's own record of them. No parameter: the user
     * comes from the service, the other way to get it (the controller parameter is shown below).
     */
    @GetMapping("/me")
    public UserProfile profile() {
        return authService.profile();
    }

    /** Only users with the Casdoor role {@code admin}: a URL rule in {@code SecurityConfig}. */
    @GetMapping("/admin")
    public Map<String, Object> admin(@CurrentUser AuthUser user) {
        return Map.of("message", "Hello admin " + user.getId());
    }

    /** Only users with the Casdoor role {@code user}: method security, checked before the method runs. */
    @PreAuthorize("hasRole('user')")
    @GetMapping("/user")
    public Map<String, Object> user(@CurrentUser AuthUser user) {
        return Map.of("message", "Hello user " + user.getId());
    }
}
