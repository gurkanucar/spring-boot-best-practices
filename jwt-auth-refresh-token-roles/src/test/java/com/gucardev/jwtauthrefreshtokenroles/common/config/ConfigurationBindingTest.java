package com.gucardev.jwtauthrefreshtokenroles.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;

@SpringBootTest
class ConfigurationBindingTest {

    @Autowired
    private JwtProperties jwtProperties;

    @Autowired
    private AppSecurityProperties securityProperties;

    @Autowired
    private OtpProperties otpProperties;

    @Autowired
    private AppProperties appProperties;

    @Test
    void bindsJwtProperties() {
        assertThat(jwtProperties.issuer()).isEqualTo("http://localhost:8102");
        assertThat(jwtProperties.keyId()).isEqualTo("demo-key-1");
        assertThat(jwtProperties.privateKey().exists()).isTrue();
        assertThat(jwtProperties.publicKey().exists()).isTrue();
        assertThat(jwtProperties.accessTokenTtl()).isEqualTo(Duration.ofMinutes(30));
        assertThat(jwtProperties.refreshTokenTtl()).isEqualTo(Duration.ofDays(2));
    }

    @Autowired
    private Environment environment;

    @Test
    void smtpHasTimeouts() {
        // JavaMail waits forever by default; a black-holed SMTP host would hang every request that sends mail.
        assertThat(environment.getProperty("spring.mail.properties.mail.smtp.connectiontimeout")).isEqualTo("5000");
        assertThat(environment.getProperty("spring.mail.properties.mail.smtp.timeout")).isEqualTo("5000");
        assertThat(environment.getProperty("spring.mail.properties.mail.smtp.writetimeout")).isEqualTo("5000");
    }

    @Test
    void bindsSecurityOtpAndAppProperties() {
        assertThat(securityProperties.publicPaths())
                .contains("/api/auth/login", "/.well-known/jwks.json")
                .doesNotContain("/api/auth/change-password", "/api/auth/**");
        assertThat(securityProperties.tokenCleanupCron()).isEqualTo("-");
        assertThat(securityProperties.password().minLength()).isEqualTo(8);

        assertThat(otpProperties.cooldown()).isEqualTo(Duration.ofSeconds(60));
        assertThat(otpProperties.maxAttempts()).isEqualTo(5);
        assertThat(otpProperties.ttl().emailVerification()).isEqualTo(Duration.ofHours(24));

        assertThat(appProperties.baseUrl()).isEqualTo("http://localhost:8102");
        assertThat(appProperties.seed().adminEmail()).isEqualTo("admin@example.com");
        assertThat(appProperties.mail().from()).isEqualTo("no-reply@example.com");
    }
}
