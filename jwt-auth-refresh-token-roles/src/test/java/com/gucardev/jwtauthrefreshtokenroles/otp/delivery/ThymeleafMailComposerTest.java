package com.gucardev.jwtauthrefreshtokenroles.otp.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gucardev.jwtauthrefreshtokenroles.otp.service.OtpChannel;
import com.gucardev.jwtauthrefreshtokenroles.otp.service.OtpPurpose;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

class ThymeleafMailComposerTest {

    private static final Instant NOW = Instant.parse("2026-09-30T10:00:00Z");
    private static final String LINK = "http://localhost:8102/api/auth/reset-password?token=abc";

    private final ThymeleafMailComposer composer = new ThymeleafMailComposer(engine(), Clock.fixed(NOW, ZoneOffset.UTC));

    // SpringTemplateEngine evaluates ${...} with SpEL; the plain TemplateEngine would need OGNL,
    // which the Spring Boot starter does not bring.
    private static SpringTemplateEngine engine() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        return engine;
    }

    private static OtpMessage message(OtpPurpose purpose, Duration validFor) {
        return new OtpMessage(purpose, OtpChannel.EMAIL, "jane@example.com", "abc", LINK, NOW.plus(validFor));
    }

    @Test
    void passwordResetMailContainsLinkAndValidity() {
        MailContent mail = composer.compose(message(OtpPurpose.PASSWORD_RESET, Duration.ofMinutes(15)));

        assertThat(mail.subject()).isEqualTo("Reset your password");
        assertThat(mail.html()).contains(LINK).contains("15 minutes");
    }

    @Test
    void verificationMailContainsLink() {
        MailContent mail = composer.compose(message(OtpPurpose.EMAIL_VERIFICATION, Duration.ofHours(24)));

        assertThat(mail.subject()).isEqualTo("Confirm your e-mail address");
        assertThat(mail.html()).contains(LINK).contains("1440 minutes");
    }

    @Test
    void mailsLoadNoExternalResources() {
        for (OtpPurpose purpose : new OtpPurpose[] {OtpPurpose.PASSWORD_RESET, OtpPurpose.EMAIL_VERIFICATION}) {
            String html = composer.compose(message(purpose, Duration.ofMinutes(15))).html();
            assertThat(html).doesNotContain("<script", "<link", "src=");
        }
    }

    @Test
    void phoneVerificationIsNotAnEmail() {
        assertThatThrownBy(() -> composer.compose(message(OtpPurpose.PHONE_VERIFICATION, Duration.ofMinutes(5))))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
