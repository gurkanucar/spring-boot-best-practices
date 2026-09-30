package com.gucardev.jwtauthrefreshtokenroles.token.access;

import com.gucardev.jwtauthrefreshtokenroles.token.access.claims.StandardClaims;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.oauth2.jwt.Jwt;

/** The caller as described by the access token. Controllers get the Jwt via @AuthenticationPrincipal. */
public record CurrentUser(UUID id, String email, List<String> roles, Map<String, Object> claims) {

    public static CurrentUser from(Jwt jwt) {
        List<String> roles = jwt.getClaimAsStringList(StandardClaims.ROLES);
        return new CurrentUser(
                UUID.fromString(jwt.getSubject()),
                jwt.getClaimAsString(StandardClaims.EMAIL),
                roles == null ? List.of() : roles,
                jwt.getClaims());
    }

    public Optional<Object> claim(String name) {
        return Optional.ofNullable(claims.get(name));
    }
}
