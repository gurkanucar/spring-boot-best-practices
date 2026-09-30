package com.gucardev.jwtauthrefreshtokenroles.otp.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import com.gucardev.jwtauthrefreshtokenroles.common.security.TokenHasher;
import com.gucardev.jwtauthrefreshtokenroles.otp.delivery.OtpMessage;
import com.gucardev.jwtauthrefreshtokenroles.otp.store.OneTimeCodeEntity;
import com.gucardev.jwtauthrefreshtokenroles.role.entity.RoleName;
import com.gucardev.jwtauthrefreshtokenroles.support.IntegrationTestSupport;
import com.gucardev.jwtauthrefreshtokenroles.user.entity.UserEntity;
import jakarta.mail.internet.MimeMessage;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.MailSendException;

class OtpServiceTest extends IntegrationTestSupport {

    @Autowired
    private OtpService otpService;

    @Autowired
    private OtpCodeHasher otpCodeHasher;

    @Autowired
    private TokenHasher tokenHasher;

    private UserEntity verifiedUser() {
        return createUser(uniqueEmail(), PASSWORD, true, RoleName.USER);
    }

    private UserEntity withPendingPhone(UserEntity user, String phone) {
        return transactionTemplate.execute(status -> {
            UserEntity managed = userRepository.findById(user.getId()).orElseThrow();
            managed.setPendingPhone(phone);
            return managed;
        });
    }

    @Test
    void phoneVerificationCodeGoesToThePendingNumberAndResetToTheVerifiedOne() {
        String verified = uniquePhone();
        String pending = uniquePhone();
        UserEntity user = withPendingPhone(createUserWithVerifiedPhone(uniqueEmail(), verified), pending);

        otpService.issue(user, OtpPurpose.PHONE_VERIFICATION, OtpChannel.SMS);
        otpService.issue(user, OtpPurpose.PASSWORD_RESET, OtpChannel.SMS);

        assertThat(lastSms(pending).purpose()).isEqualTo(OtpPurpose.PHONE_VERIFICATION);
        assertThat(lastSms(verified).purpose()).isEqualTo(OtpPurpose.PASSWORD_RESET);
    }

    /** Wrong code guaranteed to differ from the real one. */
    private static String wrongCode(String code) {
        return code.equals("000000") ? "111111" : "000000";
    }

    @Test
    void emailLinkIsCheckedWithoutConsumingAndVerifiedOnce() {
        UserEntity user = verifiedUser();

        assertThat(otpService.issue(user, OtpPurpose.PASSWORD_RESET, OtpChannel.EMAIL)).isTrue();

        OtpMessage mail = lastEmail(user.getEmail());
        assertThat(mail.link()).isEqualTo("http://localhost:8102/api/auth/reset-password?token=" + mail.code());
        assertThat(otpService.checkLink(mail.code(), OtpPurpose.PASSWORD_RESET)).isTrue();
        assertThat(otpService.checkLink(mail.code(), OtpPurpose.PASSWORD_RESET)).isTrue();
        assertThat(otpService.checkLink(mail.code(), OtpPurpose.EMAIL_VERIFICATION)).isFalse();

        assertThat(otpService.verifyLink(mail.code(), OtpPurpose.PASSWORD_RESET))
                .isEqualTo(new OtpVerificationResult.Valid(user.getId()));
        assertThat(otpService.verifyLink(mail.code(), OtpPurpose.PASSWORD_RESET))
                .isEqualTo(OtpVerificationResult.INVALID);
    }

    @Test
    void storesOnlyTheHashOfTheLinkToken() {
        UserEntity user = verifiedUser();
        otpService.issue(user, OtpPurpose.EMAIL_VERIFICATION, OtpChannel.EMAIL);
        String token = lastEmail(user.getEmail()).code();

        OneTimeCodeEntity stored = oneTimeCodeRepository
                .findByUserIdAndPurposeAndChannel(user.getId(), OtpPurpose.EMAIL_VERIFICATION, OtpChannel.EMAIL)
                .orElseThrow();
        assertThat(stored.getCodeHash()).isEqualTo(tokenHasher.hash(token)).isNotEqualTo(token);
    }

    @Test
    void cooldownSkipsSecondRequestAndKeepsFirstCode() {
        UserEntity user = verifiedUser();
        otpService.issue(user, OtpPurpose.PASSWORD_RESET, OtpChannel.EMAIL);
        String first = lastEmail(user.getEmail()).code();

        assertThat(otpService.issue(user, OtpPurpose.PASSWORD_RESET, OtpChannel.EMAIL)).isFalse();

        assertThat(sentEmails(user.getEmail())).hasSize(1);
        assertThat(otpService.checkLink(first, OtpPurpose.PASSWORD_RESET)).isTrue();
    }

    @Test
    void afterCooldownNewCodeReplacesTheOldOne() {
        UserEntity user = verifiedUser();
        otpService.issue(user, OtpPurpose.PASSWORD_RESET, OtpChannel.EMAIL);
        String first = lastEmail(user.getEmail()).code();
        ageCodes(user.getId(), Duration.ofSeconds(61));

        assertThat(otpService.issue(user, OtpPurpose.PASSWORD_RESET, OtpChannel.EMAIL)).isTrue();

        String second = lastEmail(user.getEmail()).code();
        assertThat(second).isNotEqualTo(first);
        assertThat(otpService.checkLink(first, OtpPurpose.PASSWORD_RESET)).isFalse();
        assertThat(otpService.checkLink(second, OtpPurpose.PASSWORD_RESET)).isTrue();
    }

    @Test
    void emailAndSmsCodesAreIndependent() {
        String phone = uniquePhone();
        UserEntity user = createUserWithVerifiedPhone(uniqueEmail(), phone);

        otpService.issue(user, OtpPurpose.PASSWORD_RESET, OtpChannel.EMAIL);
        otpService.issue(user, OtpPurpose.PASSWORD_RESET, OtpChannel.SMS);

        String link = lastEmail(user.getEmail()).code();
        OtpMessage sms = lastSms(phone);
        assertThat(sms.code()).matches("\\d{6}");
        assertThat(sms.link()).isNull();
        assertThat(otpService.checkLink(link, OtpPurpose.PASSWORD_RESET)).isTrue();
        assertThat(otpService.verifyCode(user.getId(), OtpPurpose.PASSWORD_RESET, sms.code()))
                .isEqualTo(new OtpVerificationResult.Valid(user.getId()));
        assertThat(otpService.checkLink(link, OtpPurpose.PASSWORD_RESET)).isTrue();
    }

    @Test
    void smsCodeIsStoredAsHmac() {
        String phone = uniquePhone();
        UserEntity user = createUserWithVerifiedPhone(uniqueEmail(), phone);
        otpService.issue(user, OtpPurpose.PASSWORD_RESET, OtpChannel.SMS);
        String code = lastSms(phone).code();

        OneTimeCodeEntity stored = oneTimeCodeRepository
                .findByUserIdAndPurposeAndChannel(user.getId(), OtpPurpose.PASSWORD_RESET, OtpChannel.SMS)
                .orElseThrow();
        assertThat(stored.getCodeHash())
                .isEqualTo(otpCodeHasher.hash(user.getId(), OtpPurpose.PASSWORD_RESET, OtpChannel.SMS, code))
                .isNotEqualTo(tokenHasher.hash(code));
    }

    @Test
    void fourWrongGuessesStillAllowTheRightCode() {
        String phone = uniquePhone();
        UserEntity user = createUserWithVerifiedPhone(uniqueEmail(), phone);
        otpService.issue(user, OtpPurpose.PASSWORD_RESET, OtpChannel.SMS);
        String code = lastSms(phone).code();

        for (int i = 0; i < 4; i++) {
            assertThat(otpService.verifyCode(user.getId(), OtpPurpose.PASSWORD_RESET, wrongCode(code)))
                    .isEqualTo(OtpVerificationResult.INVALID);
        }
        assertThat(otpService.verifyCode(user.getId(), OtpPurpose.PASSWORD_RESET, code))
                .isInstanceOf(OtpVerificationResult.Valid.class);
    }

    @Test
    void fifthWrongGuessBurnsTheCode() {
        String phone = uniquePhone();
        UserEntity user = createUserWithVerifiedPhone(uniqueEmail(), phone);
        otpService.issue(user, OtpPurpose.PASSWORD_RESET, OtpChannel.SMS);
        String code = lastSms(phone).code();

        for (int i = 0; i < 5; i++) {
            otpService.verifyCode(user.getId(), OtpPurpose.PASSWORD_RESET, wrongCode(code));
        }

        assertThat(otpService.verifyCode(user.getId(), OtpPurpose.PASSWORD_RESET, code))
                .isEqualTo(OtpVerificationResult.INVALID);
    }

    @Test
    void codeAtTheAttemptLimitIsRejectedEvenWhenCorrect() {
        // Simulates many parallel guesses that all read the row before any of them counted.
        String phone = uniquePhone();
        UserEntity user = createUserWithVerifiedPhone(uniqueEmail(), phone);
        otpService.issue(user, OtpPurpose.PASSWORD_RESET, OtpChannel.SMS);
        String code = lastSms(phone).code();
        transactionTemplate.executeWithoutResult(status -> oneTimeCodeRepository
                .findByUserIdAndPurposeAndChannel(user.getId(), OtpPurpose.PASSWORD_RESET, OtpChannel.SMS)
                .orElseThrow()
                .setAttempts(5));

        assertThat(otpService.verifyCode(user.getId(), OtpPurpose.PASSWORD_RESET, code))
                .isEqualTo(OtpVerificationResult.INVALID);
    }

    @Test
    void burnedCodeBlocksANewCodeUntilItExpires() {
        String phone = uniquePhone();
        UserEntity user = createUserWithVerifiedPhone(uniqueEmail(), phone);
        otpService.issue(user, OtpPurpose.PASSWORD_RESET, OtpChannel.SMS);
        String code = lastSms(phone).code();
        for (int i = 0; i < 5; i++) {
            otpService.verifyCode(user.getId(), OtpPurpose.PASSWORD_RESET, wrongCode(code));
        }
        ageCodes(user.getId(), Duration.ofSeconds(61));

        assertThat(otpService.issue(user, OtpPurpose.PASSWORD_RESET, OtpChannel.SMS)).isFalse();
        assertThat(sentSms(phone)).hasSize(1);
    }

    @Test
    void linkIsSingleUseUnderConcurrency() throws Exception {
        UserEntity user = verifiedUser();
        otpService.issue(user, OtpPurpose.PASSWORD_RESET, OtpChannel.EMAIL);
        String token = lastEmail(user.getEmail()).code();

        long valid = concurrently(8, () -> otpService.verifyLink(token, OtpPurpose.PASSWORD_RESET));

        assertThat(valid).isEqualTo(1);
    }

    @Test
    void smsCodeIsSingleUseUnderConcurrency() throws Exception {
        String phone = uniquePhone();
        UserEntity user = createUserWithVerifiedPhone(uniqueEmail(), phone);
        otpService.issue(user, OtpPurpose.PASSWORD_RESET, OtpChannel.SMS);
        String code = lastSms(phone).code();

        long valid = concurrently(8, () -> otpService.verifyCode(user.getId(), OtpPurpose.PASSWORD_RESET, code));

        assertThat(valid).isEqualTo(1);
    }

    @Test
    void concurrentRequestsForTheSameCodeDoNotFail() throws Exception {
        // A double-clicked "forgot password": both requests must end normally, one code is sent.
        UserEntity user = verifiedUser();
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService pool = Executors.newFixedThreadPool(8)) {
            List<Future<Boolean>> results = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                results.add(pool.submit(() -> {
                    start.await();
                    return otpService.issue(user, OtpPurpose.PASSWORD_RESET, OtpChannel.EMAIL);
                }));
            }
            start.countDown();
            for (Future<Boolean> result : results) {
                result.get(30, TimeUnit.SECONDS);
            }
        }

        assertThat(sentEmails(user.getEmail())).hasSize(1);
    }

    @Test
    void codeIsSentOnlyAfterTheTransactionCommits() {
        UserEntity user = verifiedUser();

        transactionTemplate.executeWithoutResult(status -> {
            otpService.issue(user, OtpPurpose.PASSWORD_RESET, OtpChannel.EMAIL);
            assertThat(sentEmails(user.getEmail())).isEmpty();
            status.setRollbackOnly();
        });

        assertThat(sentEmails(user.getEmail())).isEmpty();
    }

    /** Runs the call on several threads released at the same moment; returns how many got Valid. */
    private static long concurrently(int threads, Callable<OtpVerificationResult> call) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService pool = Executors.newFixedThreadPool(threads)) {
            List<Future<OtpVerificationResult>> results = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                results.add(pool.submit(() -> {
                    start.await();
                    return call.call();
                }));
            }
            start.countDown();
            long valid = 0;
            for (Future<OtpVerificationResult> result : results) {
                if (result.get(30, TimeUnit.SECONDS) instanceof OtpVerificationResult.Valid) {
                    valid++;
                }
            }
            return valid;
        }
    }

    @Test
    void expiredLinkIsInvalid() {
        UserEntity user = verifiedUser();
        otpService.issue(user, OtpPurpose.PASSWORD_RESET, OtpChannel.EMAIL);
        String token = lastEmail(user.getEmail()).code();
        transactionTemplate.executeWithoutResult(status -> oneTimeCodeRepository
                .findByUserIdAndPurposeAndChannel(user.getId(), OtpPurpose.PASSWORD_RESET, OtpChannel.EMAIL)
                .orElseThrow()
                .setExpiresAt(Instant.now().minusSeconds(1)));

        assertThat(otpService.checkLink(token, OtpPurpose.PASSWORD_RESET)).isFalse();
        assertThat(otpService.verifyLink(token, OtpPurpose.PASSWORD_RESET)).isEqualTo(OtpVerificationResult.INVALID);
    }

    @Test
    void linkSentToOldAddressIsInvalidAfterEmailChange() {
        UserEntity user = verifiedUser();
        otpService.issue(user, OtpPurpose.PASSWORD_RESET, OtpChannel.EMAIL);
        String token = lastEmail(user.getEmail()).code();
        transactionTemplate.executeWithoutResult(status ->
                userRepository.findById(user.getId()).orElseThrow().setEmail(uniqueEmail()));

        assertThat(otpService.verifyLink(token, OtpPurpose.PASSWORD_RESET)).isEqualTo(OtpVerificationResult.INVALID);
    }

    @Test
    void cooldownDoesNotBlockACodeForANewTarget() {
        String oldPhone = uniquePhone();
        UserEntity user = withPendingPhone(verifiedUser(), oldPhone);
        otpService.issue(user, OtpPurpose.PHONE_VERIFICATION, OtpChannel.SMS);
        String newPhone = uniquePhone();
        UserEntity changed = withPendingPhone(user, newPhone);

        assertThat(otpService.issue(changed, OtpPurpose.PHONE_VERIFICATION, OtpChannel.SMS)).isTrue();
        assertThat(sentSms(newPhone)).hasSize(1);
    }

    @Test
    void deliveryFailureDoesNotPropagate() {
        UserEntity user = verifiedUser();
        doThrow(new MailSendException("SMTP down")).when(javaMailSender).send(any(MimeMessage.class));

        assertThat(otpService.issue(user, OtpPurpose.PASSWORD_RESET, OtpChannel.EMAIL)).isTrue();
        assertThat(oneTimeCodeRepository.findByUserIdAndPurposeAndChannel(
                user.getId(), OtpPurpose.PASSWORD_RESET, OtpChannel.EMAIL)).isPresent();
    }
}
