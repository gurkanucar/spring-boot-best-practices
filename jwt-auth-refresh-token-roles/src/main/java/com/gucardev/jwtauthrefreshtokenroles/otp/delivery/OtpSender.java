package com.gucardev.jwtauthrefreshtokenroles.otp.delivery;

import com.gucardev.jwtauthrefreshtokenroles.otp.service.OtpChannel;

/** Delivery strategy: exactly one bean per channel. Add a provider (Twilio, SES, ...) by implementing this. */
public interface OtpSender {

    OtpChannel channel();

    void send(OtpMessage message);
}
