package com.gucardev.logtobasicsecurity.home;

import com.gucardev.logtobasicsecurity.security.CurrentUser;
import com.gucardev.logtobasicsecurity.user.AppUser;
import com.gucardev.logtobasicsecurity.user.AppUserService;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class HomeController {

    private final AppUserService appUserService;

    /** Public. */
    @GetMapping("/")
    public Map<String, Object> home(@CurrentUser OidcUser user) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("signedIn", user != null);
        result.put("signIn", "/oauth2/authorization/logto");
        result.put("me", "/me");
        result.put("admin", "/admin (Logto role: admin)");
        result.put("user", "/user (Logto role: user)");
        result.put("reports", "/reports (Logto role: admin or user)");
        result.put("signOut", "/logout");
        return result;
    }

    /**
     * Signed-in users: what Logto told us about them ({@link OidcUser}, enough on its own to know who
     * is calling), and the application's own record of them.
     */
    @GetMapping("/me")
    public Me me(@CurrentUser OidcUser user) {
        LocalUser localUser = appUserService.findByLogtoId(user.getSubject())
                .map(LocalUser::of)
                .orElse(null);
        List<String> authorities = user.getAuthorities().stream().map(GrantedAuthority::getAuthority).sorted().toList();
        return new Me(user.getSubject(), user.getEmail(), authorities, localUser, user.getClaims());
    }

    /** Only users with the Logto role {@code admin}: a URL rule in {@code SecurityConfig}. */
    @GetMapping("/admin")
    public Map<String, Object> admin(@CurrentUser OidcUser user) {
        return Map.of("message", "Hello admin " + user.getSubject());
    }

    /** Only users with the Logto role {@code user}: method security, checked before the method runs. */
    @PreAuthorize("hasRole('user')")
    @GetMapping("/user")
    public Map<String, Object> user(@CurrentUser OidcUser user) {
        return Map.of("message", "Hello user " + user.getSubject());
    }

    /** @param claims everything Logto sent: ID token claims merged with the userinfo response */
    public record Me(String logtoId, String email, List<String> authorities, LocalUser localUser,
                     Map<String, Object> claims) {
    }

    public record LocalUser(Long id, String name, Instant createdAt, Instant lastSignInAt) {

        static LocalUser of(AppUser user) {
            return new LocalUser(user.getId(), user.getName(), user.getCreatedAt(), user.getLastSignInAt());
        }
    }
}
