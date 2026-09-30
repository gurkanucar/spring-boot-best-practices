package com.gucardev.jwtauthrefreshtokenroles.otp.store;

import static org.assertj.core.api.Assertions.assertThat;

import com.gucardev.jwtauthrefreshtokenroles.otp.service.OtpChannel;
import com.gucardev.jwtauthrefreshtokenroles.otp.service.OtpPurpose;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class JpaOtpStoreTest {

    @Autowired
    private JpaOtpStore store;

    private static OtpRecord record(UUID userId, OtpPurpose purpose, OtpChannel channel, String hash, Duration ttl) {
        Instant now = Instant.now();
        return new OtpRecord(null, userId, purpose, channel, hash, "target", now.plus(ttl), 0, now);
    }

    @Test
    void replaceKeepsOneCodePerUserPurposeAndChannel() {
        UUID user = UUID.randomUUID();
        store.replace(record(user, OtpPurpose.PASSWORD_RESET, OtpChannel.EMAIL, "old-" + user, Duration.ofMinutes(15)));
        store.replace(record(user, OtpPurpose.PASSWORD_RESET, OtpChannel.EMAIL, "new-" + user, Duration.ofMinutes(15)));

        assertThat(store.find(user, OtpPurpose.PASSWORD_RESET, OtpChannel.EMAIL))
                .get().extracting(OtpRecord::codeHash).isEqualTo("new-" + user);
        assertThat(store.findByCodeHash("old-" + user, OtpPurpose.PASSWORD_RESET, OtpChannel.EMAIL)).isEmpty();
    }

    @Test
    void channelsAreIndependent() {
        UUID user = UUID.randomUUID();
        store.replace(record(user, OtpPurpose.PASSWORD_RESET, OtpChannel.EMAIL, "mail-" + user, Duration.ofMinutes(15)));
        store.replace(record(user, OtpPurpose.PASSWORD_RESET, OtpChannel.SMS, "sms-" + user, Duration.ofMinutes(15)));

        assertThat(store.find(user, OtpPurpose.PASSWORD_RESET, OtpChannel.EMAIL)).isPresent();
        assertThat(store.find(user, OtpPurpose.PASSWORD_RESET, OtpChannel.SMS)).isPresent();

        store.deleteAll(user, OtpPurpose.PASSWORD_RESET);

        assertThat(store.find(user, OtpPurpose.PASSWORD_RESET, OtpChannel.EMAIL)).isEmpty();
        assertThat(store.find(user, OtpPurpose.PASSWORD_RESET, OtpChannel.SMS)).isEmpty();
    }

    @Test
    void attemptsStopAtTheLimitAndTheCodeStaysStored() {
        UUID user = UUID.randomUUID();
        OtpRecord saved = store.replace(record(user, OtpPurpose.PASSWORD_RESET, OtpChannel.SMS, "h-" + user, Duration.ofMinutes(15)));

        for (int i = 1; i <= 5; i++) {
            assertThat(store.tryReserveAttempt(saved.id(), 5)).isTrue();
        }
        assertThat(store.tryReserveAttempt(saved.id(), 5)).isFalse();

        // Kept until it expires, so it keeps blocking a new code for the same target.
        assertThat(store.find(user, OtpPurpose.PASSWORD_RESET, OtpChannel.SMS)).get()
                .extracting(OtpRecord::attempts).isEqualTo(5);
    }

    @Test
    void deleteReportsWhetherThisCallRemovedTheCode() {
        UUID user = UUID.randomUUID();
        OtpRecord saved = store.replace(record(user, OtpPurpose.PASSWORD_RESET, OtpChannel.EMAIL, "d-" + user, Duration.ofMinutes(15)));

        assertThat(store.delete(saved.id())).isTrue();
        assertThat(store.delete(saved.id())).isFalse();
    }

    @Test
    void deleteExpiredRemovesOnlyExpiredCodes() {
        UUID user = UUID.randomUUID();
        store.replace(record(user, OtpPurpose.PASSWORD_RESET, OtpChannel.EMAIL, "x-" + user, Duration.ofSeconds(-1)));
        store.replace(record(user, OtpPurpose.EMAIL_VERIFICATION, OtpChannel.EMAIL, "y-" + user, Duration.ofHours(1)));

        store.deleteExpired(Instant.now());

        assertThat(store.find(user, OtpPurpose.PASSWORD_RESET, OtpChannel.EMAIL)).isEmpty();
        assertThat(store.find(user, OtpPurpose.EMAIL_VERIFICATION, OtpChannel.EMAIL)).isPresent();
        assertThat(store.name()).isEqualTo("one-time codes");
    }
}
