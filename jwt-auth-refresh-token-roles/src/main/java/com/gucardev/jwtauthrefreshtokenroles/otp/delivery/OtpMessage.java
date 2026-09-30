package com.gucardev.jwtauthrefreshtokenroles.otp.delivery;

import com.gucardev.jwtauthrefreshtokenroles.otp.service.OtpChannel;
import com.gucardev.jwtauthrefreshtokenroles.otp.service.OtpPurpose;
import java.time.Instant;

/** What a sender delivers. For EMAIL, code is the link token and link is the full URL; for SMS link is null. */
public record OtpMessage(
        OtpPurpose purpose,
        OtpChannel channel,
        String target,
        String code,
        String link,
        Instant expiresAt) {
}
