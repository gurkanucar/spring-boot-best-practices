package com.gucardev.jwtauthrefreshtokenroles.otp.store;

import com.gucardev.jwtauthrefreshtokenroles.otp.service.OtpChannel;
import com.gucardev.jwtauthrefreshtokenroles.otp.service.OtpPurpose;
import java.util.Optional;
import java.util.UUID;

/** At most one code exists per (user, purpose, channel). */
public interface OtpStore {

    Optional<OtpRecord> find(UUID userId, OtpPurpose purpose, OtpChannel channel);

    Optional<OtpRecord> findByCodeHash(String codeHash, OtpPurpose purpose, OtpChannel channel);

    /** Deletes any code for the same (user, purpose, channel) and stores this one; returns it with its id. */
    OtpRecord replace(OtpRecord record);

    /**
     * Atomically takes one of the code's attempts before the guess is compared: increments the counter
     * only while it is below {@code maxAttempts}. Returns false when no attempt is left, so parallel
     * guesses cannot all slip past the limit. A code without attempts left stays stored until it
     * expires, which keeps a new one from being issued in the meantime.
     */
    boolean tryReserveAttempt(long id, int maxAttempts);

    /** Returns true only for the caller that actually deleted the code: that is the single-use check. */
    boolean delete(long id);

    /** Deletes the user's codes for this purpose on every channel. */
    void deleteAll(UUID userId, OtpPurpose purpose);
}
