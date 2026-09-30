package com.gucardev.jwtauthrefreshtokenroles.common.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.Resource;

/** Access/refresh token settings. The key files are PEM: PKCS#8 private key, X.509 public key. */
@ConfigurationProperties("security.jwt")
public record JwtProperties(
        String issuer,
        String keyId,
        Resource privateKey,
        Resource publicKey,
        Duration accessTokenTtl,
        Duration refreshTokenTtl) {
}
