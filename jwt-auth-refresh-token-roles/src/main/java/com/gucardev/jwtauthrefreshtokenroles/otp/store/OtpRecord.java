package com.gucardev.jwtauthrefreshtokenroles.otp.store;

import com.gucardev.jwtauthrefreshtokenroles.otp.service.OtpChannel;
import com.gucardev.jwtauthrefreshtokenroles.otp.service.OtpPurpose;
import java.time.Instant;
import java.util.UUID;

/** Storage-neutral view of a one-time code, so OtpStore does not leak the JPA entity. */
public record OtpRecord(
        Long id,
        UUID userId,
        OtpPurpose purpose,
        OtpChannel channel,
        String codeHash,
        String target,
        Instant expiresAt,
        int attempts,
        Instant createdAt) {

    public boolean isExpired(Instant now) {
        return !expiresAt.isAfter(now);
    }
}
