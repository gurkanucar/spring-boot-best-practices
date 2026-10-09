package com.gucardev.casdoorbasicsecurity.security;

import java.util.Map;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;

/**
 * Replaces the Casdoor registration from application.yaml in web tests: its issuer-uri would make the
 * application fetch Casdoor's discovery document at startup. This is what Spring would build from
 * that document, with made-up endpoints.
 */
@TestConfiguration
public class TestCasdoorRegistration {

    @Bean
    ClientRegistrationRepository clientRegistrationRepository() {
        return new InMemoryClientRegistrationRepository(ClientRegistration.withRegistrationId("casdoor")
                .clientId("test-client")
                .clientSecret("test-secret")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .scope("openid", "profile", "email")
                .authorizationUri("http://casdoor.test/login/oauth/authorize")
                .tokenUri("http://casdoor.test/api/login/oauth/access_token")
                .jwkSetUri("http://casdoor.test/.well-known/jwks")
                .userInfoUri("http://casdoor.test/api/userinfo")
                .userNameAttributeName("sub")
                .issuerUri("http://casdoor.test")
                .providerConfigurationMetadata(Map.of("end_session_endpoint", "http://casdoor.test/api/logout"))
                .build());
    }
}
