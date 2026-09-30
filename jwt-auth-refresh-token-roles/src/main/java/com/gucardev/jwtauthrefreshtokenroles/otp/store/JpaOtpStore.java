package com.gucardev.jwtauthrefreshtokenroles.otp.store;

import com.gucardev.jwtauthrefreshtokenroles.common.cleanup.ExpiredEntryCleaner;
import com.gucardev.jwtauthrefreshtokenroles.otp.service.OtpChannel;
import com.gucardev.jwtauthrefreshtokenroles.otp.service.OtpPurpose;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class JpaOtpStore implements OtpStore, ExpiredEntryCleaner {

    private final OneTimeCodeRepository repository;

    @Override
    @Transactional(readOnly = true)
    public Optional<OtpRecord> find(UUID userId, OtpPurpose purpose, OtpChannel channel) {
        return repository.findByUserIdAndPurposeAndChannel(userId, purpose, channel).map(JpaOtpStore::toRecord);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<OtpRecord> findByCodeHash(String codeHash, OtpPurpose purpose, OtpChannel channel) {
        return repository.findByCodeHashAndPurposeAndChannel(codeHash, purpose, channel).map(JpaOtpStore::toRecord);
    }

    @Override
    @Transactional
    public OtpRecord replace(OtpRecord record) {
        repository.deleteByKey(record.userId(), record.purpose(), record.channel());
        OneTimeCodeEntity saved = repository.save(new OneTimeCodeEntity(
                record.userId(), record.purpose(), record.channel(), record.codeHash(),
                record.target(), record.expiresAt(), record.createdAt()));
        return toRecord(saved);
    }

    // Joins the caller's transaction on purpose (no REQUIRES_NEW): a second connection per guess could
    // exhaust the pool under parallel guessing. Callers therefore must not roll back on a wrong code.
    @Override
    @Transactional
    public boolean tryReserveAttempt(long id, int maxAttempts) {
        return repository.reserveAttempt(id, maxAttempts) == 1;
    }

    @Override
    @Transactional
    public boolean delete(long id) {
        return repository.deleteCode(id) == 1;
    }

    @Override
    @Transactional
    public void deleteAll(UUID userId, OtpPurpose purpose) {
        repository.deleteByUserIdAndPurpose(userId, purpose);
    }

    @Override
    public String name() {
        return "one-time codes";
    }

    @Override
    @Transactional
    public int deleteExpired(Instant now) {
        return repository.deleteExpired(now);
    }

    private static OtpRecord toRecord(OneTimeCodeEntity e) {
        return new OtpRecord(e.getId(), e.getUserId(), e.getPurpose(), e.getChannel(), e.getCodeHash(),
                e.getTarget(), e.getExpiresAt(), e.getAttempts(), e.getCreatedAt());
    }
}
