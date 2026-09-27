package com.gucardev.logtobasicsecurity.security;

import java.util.Map;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;

/**
 * Replaces the Logto registration from application.yaml in web tests: its issuer-uri would make the
 * application fetch Logto's discovery document at startup. This is what Spring would build from
 * that document, with made-up endpoints.
 */
@TestConfiguration
public class TestLogtoRegistration {

    @Bean
    ClientRegistrationRepository clientRegistrationRepository() {
        return new InMemoryClientRegistrationRepository(ClientRegistration.withRegistrationId("logto")
                .clientId("test-client")
                .clientSecret("test-secret")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .scope("openid", "profile", "email", "roles")
                .authorizationUri("http://logto.test/oidc/auth")
                .tokenUri("http://logto.test/oidc/token")
                .jwkSetUri("http://logto.test/oidc/jwks")
                .userInfoUri("http://logto.test/oidc/me")
                .userNameAttributeName("sub")
                .issuerUri("http://logto.test/oidc")
                .providerConfigurationMetadata(Map.of("end_session_endpoint", "http://logto.test/oidc/session/end"))
                .build());
    }
}
