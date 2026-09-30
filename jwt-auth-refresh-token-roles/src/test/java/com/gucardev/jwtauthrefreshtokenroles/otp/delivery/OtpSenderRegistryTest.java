package com.gucardev.jwtauthrefreshtokenroles.otp.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gucardev.jwtauthrefreshtokenroles.otp.service.OtpChannel;
import java.util.List;
import org.junit.jupiter.api.Test;

class OtpSenderRegistryTest {

    private static OtpSender sender(OtpChannel channel) {
        return new OtpSender() {
            @Override
            public OtpChannel channel() {
                return channel;
            }

            @Override
            public void send(OtpMessage message) {
            }
        };
    }

    @Test
    void findsSenderByChannel() {
        OtpSender email = sender(OtpChannel.EMAIL);
        OtpSender sms = sender(OtpChannel.SMS);

        OtpSenderRegistry registry = new OtpSenderRegistry(List.of(email, sms));

        assertThat(registry.forChannel(OtpChannel.EMAIL)).isSameAs(email);
        assertThat(registry.forChannel(OtpChannel.SMS)).isSameAs(sms);
    }

    @Test
    void failsWhenAChannelHasNoSender() {
        assertThatThrownBy(() -> new OtpSenderRegistry(List.of(sender(OtpChannel.EMAIL))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SMS");
    }

    @Test
    void failsWhenAChannelHasTwoSenders() {
        assertThatThrownBy(() -> new OtpSenderRegistry(List.of(
                sender(OtpChannel.EMAIL), sender(OtpChannel.EMAIL), sender(OtpChannel.SMS))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("EMAIL");
    }
}
