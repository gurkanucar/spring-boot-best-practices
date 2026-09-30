package com.gucardev.jwtauthrefreshtokenroles.otp.delivery;

import java.time.Clock;
import java.time.Duration;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.context.Context;

@Component
@RequiredArgsConstructor
public class ThymeleafMailComposer implements MailComposer {

    private final ITemplateEngine templateEngine;
    private final Clock clock;

    @Override
    public MailContent compose(OtpMessage message) {
        Context context = new Context(Locale.ENGLISH);
        context.setVariable("link", message.link());
        context.setVariable("validMinutes",
                Math.max(1, Duration.between(clock.instant(), message.expiresAt()).toMinutes()));
        return switch (message.purpose()) {
            case EMAIL_VERIFICATION -> new MailContent("Confirm your e-mail address",
                    templateEngine.process("mail/email-verification", context));
            case PASSWORD_RESET -> new MailContent("Reset your password",
                    templateEngine.process("mail/password-reset", context));
            case PHONE_VERIFICATION -> throw new IllegalArgumentException(
                    "Phone verification codes are sent by SMS, not by e-mail");
        };
    }
}
