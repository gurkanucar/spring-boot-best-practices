package com.gucardev.jwtauthrefreshtokenroles.token.jwks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gucardev.jwtauthrefreshtokenroles.support.IntegrationTestSupport;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

class JwksTest extends IntegrationTestSupport {

    @Test
    void publishesOnlyThePublicKey() throws Exception {
        String body = mockMvc.perform(get("/.well-known/jwks.json"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JWKSet set = JWKSet.parse(body);
        assertThat(set.getKeys()).hasSize(1);
        JWK key = set.getKeys().getFirst();
        assertThat(key.getKeyID()).isEqualTo("demo-key-1");
        assertThat(key.isPrivate()).isFalse();
        assertThat(body).doesNotContain("\"d\"");
    }

    @Test
    void anotherServiceCanVerifyTokensWithTheJwksAlone() throws Exception {
        String body = mockMvc.perform(get("/.well-known/jwks.json"))
                .andReturn().getResponse().getContentAsString();
        JwtDecoder foreignDecoder = NimbusJwtDecoder.withJwkSource(new ImmutableJWKSet<>(JWKSet.parse(body))).build();

        Jwt jwt = foreignDecoder.decode(login(ADMIN_EMAIL, ADMIN_PASSWORD).accessToken());

        assertThat(jwt.getClaimAsString("email")).isEqualTo(ADMIN_EMAIL);
    }
}
