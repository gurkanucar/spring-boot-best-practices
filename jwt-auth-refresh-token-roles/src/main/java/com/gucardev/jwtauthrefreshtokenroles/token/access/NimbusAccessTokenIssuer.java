package com.gucardev.jwtauthrefreshtokenroles.token.access;

import com.gucardev.jwtauthrefreshtokenroles.common.config.JwtProperties;
import com.gucardev.jwtauthrefreshtokenroles.token.access.claims.JwtClaimsContributor;
import com.gucardev.jwtauthrefreshtokenroles.token.access.claims.StandardClaims;
import com.gucardev.jwtauthrefreshtokenroles.user.entity.UserEntity;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class NimbusAccessTokenIssuer implements AccessTokenIssuer {

    private final JwtEncoder jwtEncoder;
    private final JwtProperties jwtProperties;
    private final List<JwtClaimsContributor> contributors;
    private final Clock clock;

    @Override
    public IssuedAccessToken issue(UserEntity user) {
        Instant now = clock.instant();
        // Standard claims go in first, so a contributor emitting one of them is caught as a duplicate.
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer(jwtProperties.issuer())
                .subject(user.getId().toString())
                .issuedAt(now)
                .expiresAt(now.plus(jwtProperties.accessTokenTtl()))
                .claim(StandardClaims.EMAIL, user.getEmail())
                .claim(StandardClaims.ROLES, user.roleNames());
        claims.claims(map -> contributors.forEach(contributor -> merge(map, contributor, user)));

        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).keyId(jwtProperties.keyId()).build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims.build())).getTokenValue();
        return new IssuedAccessToken(token, jwtProperties.accessTokenTtl());
    }

    private static void merge(Map<String, Object> claims, JwtClaimsContributor contributor, UserEntity user) {
        Set<String> declared = contributor.declaredClaims();
        contributor.contribute(user).forEach((name, value) -> {
            if (!declared.isEmpty() && !declared.contains(name)) {
                throw new IllegalStateException("%s emitted undeclared claim '%s'"
                        .formatted(contributor.getClass().getSimpleName(), name));
            }
            if (claims.putIfAbsent(name, value) != null) {
                throw new IllegalStateException("Duplicate JWT claim: " + name);
            }
        });
    }
}
