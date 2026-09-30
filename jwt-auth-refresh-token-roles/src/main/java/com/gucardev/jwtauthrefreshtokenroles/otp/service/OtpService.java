package com.gucardev.jwtauthrefreshtokenroles.otp.service;

import com.gucardev.jwtauthrefreshtokenroles.common.config.AppProperties;
import com.gucardev.jwtauthrefreshtokenroles.common.config.OtpProperties;
import com.gucardev.jwtauthrefreshtokenroles.common.security.RandomTokenGenerator;
import com.gucardev.jwtauthrefreshtokenroles.common.security.TokenHasher;
import com.gucardev.jwtauthrefreshtokenroles.otp.delivery.OtpMessage;
import com.gucardev.jwtauthrefreshtokenroles.otp.delivery.OtpSenderRegistry;
import com.gucardev.jwtauthrefreshtokenroles.otp.store.OtpRecord;
import com.gucardev.jwtauthrefreshtokenroles.otp.store.OtpStore;
import com.gucardev.jwtauthrefreshtokenroles.user.entity.UserEntity;
import com.gucardev.jwtauthrefreshtokenroles.user.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * One-time codes for every purpose. EMAIL codes are 256-bit link tokens (stored as SHA-256); SMS
 * codes are 6 digits (stored as HMAC with the server-side pepper) and limited to otp.max-attempts.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OtpService {

    private static final int SMS_CODE_DIGITS = 6;

    private final OtpStore otpStore;
    private final OtpSenderRegistry senderRegistry;
    private final RandomTokenGenerator randomTokenGenerator;
    private final TokenHasher tokenHasher;
    private final OtpCodeHasher otpCodeHasher;
    private final UserRepository userRepository;
    private final OtpProperties otpProperties;
    private final AppProperties appProperties;
    private final Clock clock;

    /**
     * Creates and sends a code, replacing the previous one for the same purpose and channel. Returns
     * false when skipped because a code was sent to the same target less than otp.cooldown ago, or
     * the current code has used up its attempts. The code is delivered after the transaction commits,
     * so a rolled-back request never mails a dead link and no row lock is held during SMTP.
     * Delivery failures are logged, never thrown: responses must not depend on them.
     */
    @Transactional
    public boolean issue(UserEntity user, OtpPurpose purpose, OtpChannel channel) {
        // One issue per user at a time: without the lock, two parallel requests both find no code and
        // the second insert violates the unique (user_id, purpose, channel) key.
        userRepository.lockById(user.getId());
        Instant now = clock.instant();
        String target = targetOf(user, purpose, channel);
        Optional<OtpRecord> existing = otpStore.find(user.getId(), purpose, channel);
        if (existing.isPresent() && (inCooldown(existing.get(), target, now) || isBurned(existing.get(), target, now))) {
            log.debug("Skipping {} over {} for user {}: cooldown or attempts used up", purpose, channel, user.getId());
            return false;
        }

        String code;
        String codeHash;
        String link;
        if (channel == OtpChannel.EMAIL) {
            code = randomTokenGenerator.opaqueToken();
            codeHash = tokenHasher.hash(code);
            link = linkFor(purpose, code);
        } else {
            code = randomTokenGenerator.numericCode(SMS_CODE_DIGITS);
            codeHash = otpCodeHasher.hash(user.getId(), purpose, channel, code);
            link = null;
        }
        Instant expiresAt = now.plus(ttlOf(purpose));
        otpStore.replace(new OtpRecord(null, user.getId(), purpose, channel, codeHash, target, expiresAt, 0, now));

        OtpMessage message = new OtpMessage(purpose, channel, target, code, link, expiresAt);
        UUID userId = user.getId();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                deliver(message, userId);
            }
        });
        return true;
    }

    private void deliver(OtpMessage message, UUID userId) {
        try {
            senderRegistry.forChannel(message.channel()).send(message);
        } catch (RuntimeException e) {
            log.warn("Could not deliver {} code over {} to user {}", message.purpose(), message.channel(), userId, e);
        }
    }

    /** Read-only validity check for the GET pages: never consumes the link. */
    @Transactional(readOnly = true)
    public boolean checkLink(String token, OtpPurpose purpose) {
        return findValidLink(token, purpose).isPresent();
    }

    /** Valid only for the one caller whose delete removed the row: concurrent submissions of a link lose. */
    @Transactional
    public OtpVerificationResult verifyLink(String token, OtpPurpose purpose) {
        Optional<OtpRecord> record = findValidLink(token, purpose);
        if (record.isEmpty() || !otpStore.delete(record.get().id())) {
            return OtpVerificationResult.INVALID;
        }
        return new OtpVerificationResult.Valid(record.get().userId());
    }

    /**
     * The attempt is taken before the code is compared, atomically, so parallel guesses cannot all be
     * compared against the same row. The counter update lives in the caller's transaction: callers must
     * commit on an INVALID result (use noRollbackFor on their exception), or the guess is not counted.
     */
    @Transactional
    public OtpVerificationResult verifyCode(UUID userId, OtpPurpose purpose, String code) {
        Optional<OtpRecord> found = otpStore.find(userId, purpose, OtpChannel.SMS);
        if (found.isEmpty() || code == null) {
            return OtpVerificationResult.INVALID;
        }
        OtpRecord record = found.get();
        if (record.isExpired(clock.instant()) || !targetStillMatches(record)
                || !otpStore.tryReserveAttempt(record.id(), otpProperties.maxAttempts())) {
            return OtpVerificationResult.INVALID;
        }
        byte[] expected = record.codeHash().getBytes(StandardCharsets.US_ASCII);
        byte[] actual = otpCodeHasher.hash(userId, purpose, OtpChannel.SMS, code).getBytes(StandardCharsets.US_ASCII);
        if (!MessageDigest.isEqual(expected, actual) || !otpStore.delete(record.id())) {
            return OtpVerificationResult.INVALID;
        }
        return new OtpVerificationResult.Valid(userId);
    }

    @Transactional
    public void deleteAll(UUID userId, OtpPurpose purpose) {
        otpStore.deleteAll(userId, purpose);
    }

    private Optional<OtpRecord> findValidLink(String token, OtpPurpose purpose) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        Instant now = clock.instant();
        return otpStore.findByCodeHash(tokenHasher.hash(token), purpose, OtpChannel.EMAIL)
                .filter(record -> !record.isExpired(now))
                .filter(this::targetStillMatches);
    }

    /** A code is void once the user's address for that purpose differs from where it was sent. */
    private boolean targetStillMatches(OtpRecord record) {
        return userRepository.findById(record.userId())
                .map(user -> record.target().equals(addressFor(user, record.purpose(), record.channel())))
                .orElse(false);
    }

    private boolean inCooldown(OtpRecord existing, String target, Instant now) {
        return !existing.isExpired(now)
                && existing.target().equals(target)
                && existing.createdAt().plus(otpProperties.cooldown()).isAfter(now);
    }

    /**
     * A code whose attempts are used up blocks a new one for the same target until it expires;
     * otherwise requesting a fresh code would reset the attempt limit after every 5 guesses.
     */
    private boolean isBurned(OtpRecord existing, String target, Instant now) {
        return !existing.isExpired(now)
                && existing.target().equals(target)
                && existing.attempts() >= otpProperties.maxAttempts();
    }

    private static String targetOf(UserEntity user, OtpPurpose purpose, OtpChannel channel) {
        String target = addressFor(user, purpose, channel);
        if (target == null) {
            throw new IllegalStateException("User " + user.getId() + " has no " + channel + " address for " + purpose);
        }
        return target;
    }

    /** Phone verification texts the pending number; everything else by SMS goes to the verified one. */
    private static String addressFor(UserEntity user, OtpPurpose purpose, OtpChannel channel) {
        if (channel == OtpChannel.EMAIL) {
            return user.getEmail();
        }
        return purpose == OtpPurpose.PHONE_VERIFICATION ? user.getPendingPhone() : user.getPhone();
    }

    private String linkFor(OtpPurpose purpose, String token) {
        String path = switch (purpose) {
            case EMAIL_VERIFICATION -> "/api/auth/verify-email";
            case PASSWORD_RESET -> "/api/auth/reset-password";
            case PHONE_VERIFICATION -> throw new IllegalArgumentException("Phone verification has no link");
        };
        return appProperties.baseUrl() + path + "?token=" + token;
    }

    private Duration ttlOf(OtpPurpose purpose) {
        return switch (purpose) {
            case PASSWORD_RESET -> otpProperties.ttl().passwordReset();
            case EMAIL_VERIFICATION -> otpProperties.ttl().emailVerification();
            case PHONE_VERIFICATION -> otpProperties.ttl().phoneVerification();
        };
    }
}
