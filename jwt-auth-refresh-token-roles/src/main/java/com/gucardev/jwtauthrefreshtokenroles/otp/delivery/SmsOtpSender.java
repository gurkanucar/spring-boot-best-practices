package com.gucardev.jwtauthrefreshtokenroles.otp.delivery;

import com.gucardev.jwtauthrefreshtokenroles.otp.service.OtpChannel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** Stand-in for an SMS provider: the code is only written to the log. */
@Slf4j
@Component
public class SmsOtpSender implements OtpSender {

    @Override
    public OtpChannel channel() {
        return OtpChannel.SMS;
    }

    @Override
    public void send(OtpMessage message) {
        log.info("SMS to {}: your {} code is {}", message.target(), message.purpose(), message.code());
    }
}
