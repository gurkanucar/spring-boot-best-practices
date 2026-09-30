package com.gucardev.jwtauthrefreshtokenroles.common.cleanup;

import static org.assertj.core.api.Assertions.assertThat;

import com.gucardev.jwtauthrefreshtokenroles.common.security.TokenHasher;
import com.gucardev.jwtauthrefreshtokenroles.otp.service.OtpChannel;
import com.gucardev.jwtauthrefreshtokenroles.otp.service.OtpPurpose;
import com.gucardev.jwtauthrefreshtokenroles.otp.store.OtpRecord;
import com.gucardev.jwtauthrefreshtokenroles.otp.store.OtpStore;
import com.gucardev.jwtauthrefreshtokenroles.token.refresh.RefreshTokenRepository;
import com.gucardev.jwtauthrefreshtokenroles.token.refresh.RefreshTokenStore;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class ExpiredEntryCleanupJobTest {

    @Autowired
    private ExpiredEntryCleanupJob job;

    @Autowired
    private RefreshTokenStore refreshTokenStore;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private OtpStore otpStore;

    @Autowired
    private TokenHasher tokenHasher;

    @Test
    void deletesExpiredRefreshTokensAndCodesAndKeepsValidOnes() {
        String userId = UUID.randomUUID().toString();
        String expiredToken = "expired-" + userId;
        String validToken = "valid-" + userId;
        refreshTokenStore.save(expiredToken, userId, Duration.ofSeconds(-1));
        refreshTokenStore.save(validToken, userId, Duration.ofDays(2));

        UUID user = UUID.fromString(userId);
        Instant now = Instant.now();
        otpStore.replace(new OtpRecord(null, user, OtpPurpose.PASSWORD_RESET, OtpChannel.EMAIL,
                "old-" + userId, "t", now.minusSeconds(1), 0, now.minusSeconds(900)));
        otpStore.replace(new OtpRecord(null, user, OtpPurpose.EMAIL_VERIFICATION, OtpChannel.EMAIL,
                "new-" + userId, "t", now.plusSeconds(3600), 0, now));

        job.cleanUp();

        assertThat(refreshTokenRepository.findById(tokenHasher.hash(expiredToken))).isEmpty();
        assertThat(refreshTokenRepository.findById(tokenHasher.hash(validToken))).isPresent();
        assertThat(otpStore.find(user, OtpPurpose.PASSWORD_RESET, OtpChannel.EMAIL)).isEmpty();
        assertThat(otpStore.find(user, OtpPurpose.EMAIL_VERIFICATION, OtpChannel.EMAIL)).isPresent();
    }
}
