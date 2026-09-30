package com.gucardev.jwtauthrefreshtokenroles.otp.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gucardev.jwtauthrefreshtokenroles.common.config.AppProperties;
import com.gucardev.jwtauthrefreshtokenroles.otp.service.OtpChannel;
import com.gucardev.jwtauthrefreshtokenroles.otp.service.OtpPurpose;
import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.javamail.JavaMailSender;

class EmailOtpSenderTest {

    @Test
    void sendsHtmlMailToTheTarget() throws Exception {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        when(mailSender.createMimeMessage()).thenReturn(new MimeMessage((Session) null));
        MailComposer composer = message -> new MailContent("Reset your password", "<p>" + message.link() + "</p>");
        AppProperties app = new AppProperties("http://localhost:8102", null, new AppProperties.Mail("no-reply@example.com"));
        EmailOtpSender sender = new EmailOtpSender(mailSender, composer, app);

        sender.send(new OtpMessage(OtpPurpose.PASSWORD_RESET, OtpChannel.EMAIL, "jane@example.com",
                "abc", "http://localhost:8102/api/auth/reset-password?token=abc", Instant.now()));

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());
        MimeMessage mail = captor.getValue();
        assertThat(mail.getRecipients(Message.RecipientType.TO)[0].toString()).isEqualTo("jane@example.com");
        assertThat(mail.getFrom()[0].toString()).isEqualTo("no-reply@example.com");
        assertThat(mail.getSubject()).isEqualTo("Reset your password");
        assertThat((String) mail.getContent()).contains("reset-password?token=abc");
        assertThat(sender.channel()).isEqualTo(OtpChannel.EMAIL);
    }
}
