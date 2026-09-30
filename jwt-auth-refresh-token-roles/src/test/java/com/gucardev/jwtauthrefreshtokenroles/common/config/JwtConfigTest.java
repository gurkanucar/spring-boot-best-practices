package com.gucardev.jwtauthrefreshtokenroles.common.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nimbusds.jose.jwk.RSAKey;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtValidationException;

@SpringBootTest
class JwtConfigTest {

    @Autowired
    private RSAKey rsaKey;

    @Autowired
    private JwtEncoder encoder;

    @Autowired
    private JwtDecoder decoder;

    @Test
    void loadsKeyPairFromPemFiles() {
        assertThat(rsaKey.getKeyID()).isEqualTo("demo-key-1");
        assertThat(rsaKey.isPrivate()).isTrue();
    }

    @Test
    void decoderAcceptsTokenFromConfiguredIssuer() {
        String token = encode("http://localhost:8102");
        assertThat(decoder.decode(token).getSubject()).isEqualTo("someone");
    }

    @Test
    void decoderRejectsForeignIssuer() {
        String token = encode("http://evil.example");
        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtValidationException.class);
    }

    private String encode(String issuer) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer(issuer).subject("someone")
                .issuedAt(now).expiresAt(now.plusSeconds(60)).build();
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).keyId("demo-key-1").build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
