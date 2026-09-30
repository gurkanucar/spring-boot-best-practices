package com.gucardev.jwtauthrefreshtokenroles.otp.delivery;

import com.gucardev.jwtauthrefreshtokenroles.common.config.AppProperties;
import com.gucardev.jwtauthrefreshtokenroles.otp.service.OtpChannel;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class EmailOtpSender implements OtpSender {

    private final JavaMailSender mailSender;
    private final MailComposer mailComposer;
    private final AppProperties appProperties;

    @Override
    public OtpChannel channel() {
        return OtpChannel.EMAIL;
    }

    @Override
    public void send(OtpMessage message) {
        MailContent content = mailComposer.compose(message);
        MimeMessage mime = mailSender.createMimeMessage();
        try {
            MimeMessageHelper helper = new MimeMessageHelper(mime, StandardCharsets.UTF_8.name());
            helper.setFrom(appProperties.mail().from());
            helper.setTo(message.target());
            helper.setSubject(content.subject());
            helper.setText(content.html(), true);
        } catch (MessagingException e) {
            throw new IllegalStateException("Could not build the mail", e);
        }
        mailSender.send(mime);
    }
}
