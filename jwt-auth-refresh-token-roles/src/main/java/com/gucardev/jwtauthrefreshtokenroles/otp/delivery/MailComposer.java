package com.gucardev.jwtauthrefreshtokenroles.otp.delivery;

/** Renders the subject and HTML body of an OTP mail. Swap it to change the template engine. */
public interface MailComposer {

    MailContent compose(OtpMessage message);
}
