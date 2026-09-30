package com.gucardev.logtobasicsecurity.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Which decoded access tokens the API accepts: RFC 9068 access tokens ({@code typ: at+jwt}, as Logto
 * issues them), from our Logto, for our API resource. The tokens below have the shape of a real Logto
 * access token; {@code jwt()} in the MockMvc tests skips this validation, so it is tested here.
 */
class ApiTokenValidatorTest {

    private static final String ISSUER = "http://localhost:3001/oidc";
    private static final String AUDIENCE = "http://localhost:8101/api";

    private final OAuth2TokenValidator<Jwt> validator = ApiSecurityConfig.apiTokenValidator(ISSUER, AUDIENCE);

    private static Jwt token(String type, String issuer, List<String> audience) {
        Instant now = Instant.now();
        return Jwt.withTokenValue("t").header("alg", "ES384").header("typ", type)
                .issuer(issuer).audience(audience).subject("app-or-user")
                .claim("jti", "token-id").claim("client_id", "api-demo-client").claim("scope", "read:reports")
                .issuedAt(now).expiresAt(now.plusSeconds(300))
                .build();
    }

    private static Jwt accessToken(List<String> audience) {
        return token("at+jwt", ISSUER, audience);
    }

    @Test
    void acceptsALogtoAccessTokenForThisApi() {
        assertThat(validator.validate(accessToken(List.of(AUDIENCE))).hasErrors()).isFalse();
    }

    @Test
    void rejectsATokenThatIsNotAnAccessToken() {
        // An ID token (typ JWT) must not be usable as an access token, even if the audience matched.
        assertThat(validator.validate(token("JWT", ISSUER, List.of(AUDIENCE))).hasErrors()).isTrue();
    }

    @Test
    void rejectsATokenIssuedForAnotherResource() {
        // e.g. a Management API token, or a token for another service in the same Logto.
        assertThat(validator.validate(accessToken(List.of("https://default.logto.app/api"))).hasErrors()).isTrue();
    }

    @Test
    void rejectsATokenWithoutAudience() {
        assertThat(validator.validate(accessToken(List.of())).hasErrors()).isTrue();
    }

    @Test
    void rejectsATokenFromAnotherIssuer() {
        assertThat(validator.validate(token("at+jwt", "http://evil.example/oidc", List.of(AUDIENCE))).hasErrors()).isTrue();
    }
}
