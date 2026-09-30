package com.gucardev.jwtauthrefreshtokenroles.token.jwks;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public JWK Set so other services can verify our access tokens without sharing any secret, e.g. with
 * spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost:8102/.well-known/jwks.json
 */
@RestController
@RequiredArgsConstructor
public class JwksController {

    private final RSAKey rsaKey;

    @GetMapping("/.well-known/jwks.json")
    public Map<String, Object> jwks() {
        // toPublicJWK() drops the private parts; toJSONObject() would drop them too, but be explicit.
        return new JWKSet(rsaKey.toPublicJWK()).toJSONObject();
    }
}
