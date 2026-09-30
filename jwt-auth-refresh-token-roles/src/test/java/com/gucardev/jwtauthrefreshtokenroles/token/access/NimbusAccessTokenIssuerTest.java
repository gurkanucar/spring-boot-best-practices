package com.gucardev.jwtauthrefreshtokenroles.token.access;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gucardev.jwtauthrefreshtokenroles.common.config.JwtProperties;
import com.gucardev.jwtauthrefreshtokenroles.role.entity.RoleEntity;
import com.gucardev.jwtauthrefreshtokenroles.token.access.claims.JwtClaimsContributor;
import com.gucardev.jwtauthrefreshtokenroles.token.access.claims.UserClaimsContributor;
import com.gucardev.jwtauthrefreshtokenroles.token.access.claims.VerificationStatusClaimsContributor;
import com.gucardev.jwtauthrefreshtokenroles.user.entity.UserEntity;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

class NimbusAccessTokenIssuerTest {

    private static final JwtProperties PROPS = new JwtProperties(
            "http://issuer.test", "test-key", null, null, Duration.ofMinutes(30), Duration.ofDays(2));

    private JwtEncoder encoder;
    private JwtDecoder decoder;
    private UserEntity user;

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair pair = generator.generateKeyPair();
        RSAKey rsaKey = new RSAKey.Builder((RSAPublicKey) pair.getPublic())
                .privateKey((RSAPrivateKey) pair.getPrivate())
                .keyID("test-key")
                .build();
        encoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(rsaKey)));
        decoder = NimbusJwtDecoder.withPublicKey((RSAPublicKey) pair.getPublic()).build();

        user = UserEntity.create("jane@example.com", "hash", Instant.now());
        user.setId(UUID.randomUUID());
        user.setEmailVerified(true);
        user.addRole(new RoleEntity("USER"));
        user.addRole(new RoleEntity("ADMIN"));
        user.putClaim("tenant_id", "acme");
    }

    private NimbusAccessTokenIssuer issuer(JwtClaimsContributor... contributors) {
        return new NimbusAccessTokenIssuer(encoder, PROPS, List.of(contributors), Clock.systemUTC());
    }

    @Test
    void issuesSignedTokenWithStandardAndContributedClaims() {
        IssuedAccessToken issued = issuer(
                new VerificationStatusClaimsContributor(), new UserClaimsContributor()).issue(user);

        Jwt jwt = decoder.decode(issued.value());
        assertThat(issued.expiresIn()).isEqualTo(Duration.ofMinutes(30));
        assertThat(jwt.getHeaders()).containsEntry("kid", "test-key");
        assertThat(jwt.getSubject()).isEqualTo(user.getId().toString());
        assertThat(jwt.getClaimAsString("iss")).isEqualTo("http://issuer.test");
        assertThat(jwt.getClaimAsString("email")).isEqualTo("jane@example.com");
        assertThat(jwt.getClaimAsStringList("roles")).containsExactly("ADMIN", "USER");
        assertThat(jwt.getClaimAsBoolean("email_verified")).isTrue();
        assertThat(jwt.getClaimAsBoolean("phone_verified")).isFalse();
        assertThat(jwt.getClaimAsString("tenant_id")).isEqualTo("acme");
        assertThat(Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt())).isEqualTo(Duration.ofMinutes(30));
    }

    @Test
    void contributorCannotOverrideStandardClaim() {
        JwtClaimsContributor hijacker = dynamic(Map.of("email", "evil@example.com"));

        assertThatThrownBy(() -> issuer(hijacker).issue(user))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Duplicate JWT claim: email");
    }

    @Test
    void twoContributorsEmittingSameClaimFailRegardlessOfOrder() {
        JwtClaimsContributor first = dynamic(Map.of("tenant_id", "other"));

        assertThatThrownBy(() -> issuer(new UserClaimsContributor(), first).issue(user))
                .hasMessage("Duplicate JWT claim: tenant_id");
        assertThatThrownBy(() -> issuer(first, new UserClaimsContributor()).issue(user))
                .hasMessage("Duplicate JWT claim: tenant_id");
    }

    @Test
    void contributorEmittingUndeclaredClaimFails() {
        JwtClaimsContributor sloppy = new JwtClaimsContributor() {
            @Override
            public Set<String> declaredClaims() {
                return Set.of("plan");
            }

            @Override
            public Map<String, Object> contribute(UserEntity u) {
                return Map.of("plan", "pro", "extra", "x");
            }
        };

        assertThatThrownBy(() -> issuer(sloppy).issue(user))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("undeclared claim 'extra'");
    }

    private static JwtClaimsContributor dynamic(Map<String, Object> claims) {
        return new JwtClaimsContributor() {
            @Override
            public Set<String> declaredClaims() {
                return Set.of();
            }

            @Override
            public Map<String, Object> contribute(UserEntity u) {
                return claims;
            }
        };
    }
}
